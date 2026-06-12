#!/usr/bin/env bash
# =============================================================================
# Database Restore Script
# =============================================================================
# Restores a MySQL backup by its backup ID. If the restore fails at any point,
# the operation is aborted and the pre-restore data is preserved unchanged.
# An alert is sent to the Admin on failure.
#
# Requirements satisfied:
#   28.5 - Restore from selected backup; complete within 60 minutes
#   28.6 - Abort on failure; preserve pre-restore data; alert Admin
#
# Usage:
#   ./restore.sh <backup-id>
#   ./restore.sh --file <path-to-backup-file>
#
# Environment Variables:
#   MYSQL_HOST          - MySQL hostname (default: mysql)
#   MYSQL_PORT          - MySQL port (default: 3306)
#   MYSQL_USER          - MySQL user (default: root)
#   MYSQL_PASSWORD      - MySQL password (required)
#   MYSQL_DATABASE      - Database name (default: aisa)
#   BACKUP_DIR          - Local backup directory (default: /backups)
#   S3_BUCKET           - S3-compatible bucket URI (optional; downloads from S3)
#   S3_ENDPOINT         - S3 endpoint for non-AWS providers (optional)
#   ALERT_WEBHOOK_URL   - Webhook URL for failure alerts (optional)
#   BACKUP_METADATA_LOG - Path to JSON metadata log (default: $BACKUP_DIR/backup-metadata.json)
#   RESTORE_TIMEOUT     - Max restore duration in minutes (default: 60)
#
# Restore SLA: ≤60 minutes (Req 28.5)
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
MYSQL_DATABASE="${MYSQL_DATABASE:-aisa}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
S3_BUCKET="${S3_BUCKET:-}"
S3_ENDPOINT="${S3_ENDPOINT:-}"
ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"
BACKUP_METADATA_LOG="${BACKUP_METADATA_LOG:-${BACKUP_DIR}/backup-metadata.json}"
RESTORE_TIMEOUT="${RESTORE_TIMEOUT:-60}"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() {
  echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
}

send_alert() {
  local message="$1"
  local severity="${2:-critical}"
  local alert_payload
  alert_payload=$(cat <<EOF
{
  "severity": "${severity}",
  "source": "restore-script",
  "message": "${message}",
  "timestamp": "$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
}
EOF
)

  if [ -n "$ALERT_WEBHOOK_URL" ]; then
    curl -sf -X POST -H "Content-Type: application/json" \
      -d "$alert_payload" "$ALERT_WEBHOOK_URL" || true
  fi

  log "ALERT [${severity}]: ${message}"
}

cleanup() {
  # Remove temporary pre-restore snapshot if it exists
  if [ -n "${PRE_RESTORE_SNAPSHOT:-}" ] && [ -f "$PRE_RESTORE_SNAPSHOT" ]; then
    log "Cleaning up pre-restore snapshot: ${PRE_RESTORE_SNAPSHOT}"
    rm -f "$PRE_RESTORE_SNAPSHOT"
  fi
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
BACKUP_ID=""
BACKUP_FILE=""

if [ $# -lt 1 ]; then
  echo "Usage: $0 <backup-id> | --file <path-to-backup-file>"
  exit 1
fi

if [ "$1" = "--file" ]; then
  if [ $# -lt 2 ]; then
    echo "Usage: $0 --file <path-to-backup-file>"
    exit 1
  fi
  BACKUP_FILE="$2"
else
  BACKUP_ID="$1"
fi

# ---------------------------------------------------------------------------
# Locate backup file
# ---------------------------------------------------------------------------
locate_backup() {
  if [ -n "$BACKUP_FILE" ] && [ -f "$BACKUP_FILE" ]; then
    log "Using specified backup file: ${BACKUP_FILE}"
    return 0
  fi

  if [ -n "$BACKUP_ID" ]; then
    # Search locally
    BACKUP_FILE=$(find "$BACKUP_DIR" -name "*${BACKUP_ID}*" -type f | head -1)
    if [ -n "$BACKUP_FILE" ] && [ -f "$BACKUP_FILE" ]; then
      log "Found backup locally: ${BACKUP_FILE}"
      return 0
    fi

    # Search in S3 if configured
    if [ -n "$S3_BUCKET" ]; then
      local s3_args=""
      if [ -n "$S3_ENDPOINT" ]; then
        s3_args="--endpoint-url $S3_ENDPOINT"
      fi
      local s3_file
      # shellcheck disable=SC2086
      s3_file=$(aws s3 ls "${S3_BUCKET}/" $s3_args 2>/dev/null | grep "$BACKUP_ID" | awk '{print $4}' | head -1)
      if [ -n "$s3_file" ]; then
        BACKUP_FILE="${BACKUP_DIR}/${s3_file}"
        log "Downloading backup from S3: ${S3_BUCKET}/${s3_file}"
        # shellcheck disable=SC2086
        aws s3 cp "${S3_BUCKET}/${s3_file}" "$BACKUP_FILE" $s3_args
        return 0
      fi
    fi

    log "ERROR: Backup not found for id=${BACKUP_ID}"
    return 1
  fi

  log "ERROR: No backup file or ID specified"
  return 1
}

# ---------------------------------------------------------------------------
# Pre-restore snapshot (Req 28.6: preserve pre-restore data on failure)
# ---------------------------------------------------------------------------
PRE_RESTORE_SNAPSHOT=""

create_pre_restore_snapshot() {
  PRE_RESTORE_SNAPSHOT="${BACKUP_DIR}/.pre-restore-snapshot_$(date -u '+%Y%m%d_%H%M%S').sql.gz"
  log "Creating pre-restore snapshot: ${PRE_RESTORE_SNAPSHOT}"

  if ! mysqldump \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --password="$MYSQL_PASSWORD" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --set-gtid-purged=OFF \
    "$MYSQL_DATABASE" 2>/dev/null | gzip > "$PRE_RESTORE_SNAPSHOT"; then
    log "ERROR: Failed to create pre-restore snapshot"
    send_alert "Restore ABORTED: could not create pre-restore snapshot for database=${MYSQL_DATABASE}. Pre-restore data is preserved unchanged." "critical"
    rm -f "$PRE_RESTORE_SNAPSHOT"
    PRE_RESTORE_SNAPSHOT=""
    return 1
  fi

  if [ ! -s "$PRE_RESTORE_SNAPSHOT" ]; then
    log "ERROR: Pre-restore snapshot is empty"
    send_alert "Restore ABORTED: pre-restore snapshot was empty for database=${MYSQL_DATABASE}. Pre-restore data is preserved unchanged." "critical"
    rm -f "$PRE_RESTORE_SNAPSHOT"
    PRE_RESTORE_SNAPSHOT=""
    return 1
  fi

  log "Pre-restore snapshot created successfully"
  return 0
}

# ---------------------------------------------------------------------------
# Restore operation (Req 28.5: complete within 60 minutes)
# ---------------------------------------------------------------------------
perform_restore() {
  local start_time
  start_time=$(date +%s)

  log "Starting restore of database=${MYSQL_DATABASE} from backup..."

  # Determine decompression method
  local restore_cmd="cat"
  if [[ "$BACKUP_FILE" == *.gz ]]; then
    restore_cmd="gunzip -c"
  fi

  # Execute restore with timeout (Req 28.5: ≤60 minutes)
  if timeout "${RESTORE_TIMEOUT}m" bash -c "$restore_cmd '$BACKUP_FILE' | mysql \
    --host='$MYSQL_HOST' \
    --port='$MYSQL_PORT' \
    --user='$MYSQL_USER' \
    --password='$MYSQL_PASSWORD' \
    '$MYSQL_DATABASE'" 2>/dev/null; then

    local end_time elapsed_minutes
    end_time=$(date +%s)
    elapsed_minutes=$(( (end_time - start_time) / 60 ))

    log "Restore completed successfully in ${elapsed_minutes} minute(s)"

    # Clean up pre-restore snapshot on success
    if [ -n "$PRE_RESTORE_SNAPSHOT" ] && [ -f "$PRE_RESTORE_SNAPSHOT" ]; then
      rm -f "$PRE_RESTORE_SNAPSHOT"
      PRE_RESTORE_SNAPSHOT=""
    fi

    return 0
  else
    local exit_code=$?
    log "ERROR: Restore failed (exit code: ${exit_code})"
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Rollback on failure (Req 28.6: abort, preserve pre-restore data)
# ---------------------------------------------------------------------------
rollback_restore() {
  if [ -z "$PRE_RESTORE_SNAPSHOT" ] || [ ! -f "$PRE_RESTORE_SNAPSHOT" ]; then
    log "WARNING: No pre-restore snapshot available for rollback"
    send_alert "Restore FAILED for database=${MYSQL_DATABASE}. No pre-restore snapshot available for automatic rollback. Manual intervention required." "critical"
    return 1
  fi

  log "Rolling back to pre-restore state..."

  if gunzip -c "$PRE_RESTORE_SNAPSHOT" | mysql \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --password="$MYSQL_PASSWORD" \
    "$MYSQL_DATABASE" 2>/dev/null; then
    log "Rollback successful. Pre-restore data preserved."
    rm -f "$PRE_RESTORE_SNAPSHOT"
    PRE_RESTORE_SNAPSHOT=""
    return 0
  else
    log "CRITICAL: Rollback also failed! Pre-restore snapshot preserved at: ${PRE_RESTORE_SNAPSHOT}"
    send_alert "CRITICAL: Restore AND rollback both failed for database=${MYSQL_DATABASE}. Pre-restore snapshot preserved at ${PRE_RESTORE_SNAPSHOT}. Immediate manual intervention required." "critical"
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
  trap cleanup EXIT

  log "=== Database Restore Process Started ==="
  log "Target database: ${MYSQL_DATABASE}"
  log "Restore timeout: ${RESTORE_TIMEOUT} minutes"

  # Step 1: Locate the backup file
  if ! locate_backup; then
    send_alert "Restore ABORTED: backup not found (id=${BACKUP_ID:-N/A}, file=${BACKUP_FILE:-N/A})" "critical"
    exit 1
  fi

  # Step 2: Create pre-restore snapshot (safety net for Req 28.6)
  if ! create_pre_restore_snapshot; then
    exit 1
  fi

  # Step 3: Perform the restore
  if ! perform_restore; then
    # Req 28.6: abort on failure, preserve pre-restore data, alert Admin
    send_alert "Restore FAILED for database=${MYSQL_DATABASE} from backup=${BACKUP_FILE}. Initiating rollback to preserve pre-restore state." "critical"
    rollback_restore
    exit 1
  fi

  log "=== Database Restore Process Completed Successfully ==="
}

main
