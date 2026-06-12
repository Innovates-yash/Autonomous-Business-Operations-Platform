#!/usr/bin/env bash
# =============================================================================
# Automated MySQL Backup Script
# =============================================================================
# Performs a full MySQL dump of all Platform databases, tags the backup with a
# UUID and timestamp, stores it in a configured location (local directory or
# S3-compatible bucket), retries up to 3 times on failure, and sends an alert
# (webhook or log) if all retries are exhausted.
#
# Requirements satisfied:
#   28.1 - Automated backups at configurable interval ≤24h (triggered by CronJob)
#   28.2 - Retention ≥30 days (cleanup enforced below)
#   28.3 - Each backup recorded with unique ID + completion timestamp
#   28.4 - Retry 3x on failure; alert Admin on exhaustion
#
# Environment Variables:
#   MYSQL_HOST          - MySQL hostname (default: mysql)
#   MYSQL_PORT          - MySQL port (default: 3306)
#   MYSQL_USER          - MySQL user (default: root)
#   MYSQL_PASSWORD      - MySQL password (required)
#   MYSQL_DATABASE      - Database name (default: aisa)
#   BACKUP_DIR          - Local backup directory (default: /backups)
#   S3_BUCKET           - S3-compatible bucket URI (optional; if set, uploads to S3)
#   S3_ENDPOINT         - S3 endpoint for non-AWS providers (optional)
#   RETENTION_DAYS      - Backup retention in days (default: 30, minimum: 30)
#   ALERT_WEBHOOK_URL   - Webhook URL for failure alerts (optional)
#   BACKUP_METADATA_LOG - Path to JSON metadata log (default: $BACKUP_DIR/backup-metadata.json)
#   MAX_RETRIES         - Max retry attempts (default: 3)
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
RETENTION_DAYS="${RETENTION_DAYS:-30}"
ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"
BACKUP_METADATA_LOG="${BACKUP_METADATA_LOG:-${BACKUP_DIR}/backup-metadata.json}"
MAX_RETRIES="${MAX_RETRIES:-3}"

# Enforce minimum retention of 30 days (Req 28.2)
if [ "$RETENTION_DAYS" -lt 30 ]; then
  RETENTION_DAYS=30
fi

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
  "source": "backup-script",
  "message": "${message}",
  "timestamp": "$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
}
EOF
)

  if [ -n "$ALERT_WEBHOOK_URL" ]; then
    curl -sf -X POST -H "Content-Type: application/json" \
      -d "$alert_payload" "$ALERT_WEBHOOK_URL" || true
  fi

  # Always log the alert regardless of webhook availability
  log "ALERT [${severity}]: ${message}"
}

record_metadata() {
  local backup_id="$1"
  local timestamp="$2"
  local file_path="$3"
  local size_bytes="$4"
  local status="$5"

  local entry
  entry=$(cat <<EOF
{"id":"${backup_id}","timestamp":"${timestamp}","file":"${file_path}","sizeBytes":${size_bytes},"status":"${status}","database":"${MYSQL_DATABASE}","retentionDays":${RETENTION_DAYS}}
EOF
)

  # Append to JSON-lines metadata log (Req 28.3)
  mkdir -p "$(dirname "$BACKUP_METADATA_LOG")"
  echo "$entry" >> "$BACKUP_METADATA_LOG"
  log "Backup metadata recorded: id=${backup_id}, status=${status}"
}

# ---------------------------------------------------------------------------
# Main backup logic
# ---------------------------------------------------------------------------
perform_backup() {
  local backup_id
  local timestamp
  local backup_file
  local attempt=0

  backup_id="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen || python3 -c 'import uuid; print(uuid.uuid4())')"
  timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
  backup_file="${BACKUP_DIR}/${MYSQL_DATABASE}_${backup_id}_$(date -u '+%Y%m%d_%H%M%S').sql.gz"

  mkdir -p "$BACKUP_DIR"

  log "Starting backup id=${backup_id} for database=${MYSQL_DATABASE}"

  # Retry loop (Req 28.4: retry up to 3 times)
  while [ $attempt -lt $MAX_RETRIES ]; do
    attempt=$((attempt + 1))
    log "Backup attempt ${attempt}/${MAX_RETRIES}..."

    if mysqldump \
      --host="$MYSQL_HOST" \
      --port="$MYSQL_PORT" \
      --user="$MYSQL_USER" \
      --password="$MYSQL_PASSWORD" \
      --single-transaction \
      --routines \
      --triggers \
      --events \
      --set-gtid-purged=OFF \
      "$MYSQL_DATABASE" 2>/dev/null | gzip > "$backup_file"; then

      # Verify the dump is non-empty
      if [ -s "$backup_file" ]; then
        local size_bytes
        size_bytes=$(stat -c%s "$backup_file" 2>/dev/null || stat -f%z "$backup_file" 2>/dev/null || echo "0")

        log "Backup successful: ${backup_file} (${size_bytes} bytes)"

        # Upload to S3 if configured
        if [ -n "$S3_BUCKET" ]; then
          local s3_args=""
          if [ -n "$S3_ENDPOINT" ]; then
            s3_args="--endpoint-url $S3_ENDPOINT"
          fi
          # shellcheck disable=SC2086
          if aws s3 cp "$backup_file" "${S3_BUCKET}/$(basename "$backup_file")" $s3_args; then
            log "Backup uploaded to S3: ${S3_BUCKET}/$(basename "$backup_file")"
          else
            log "WARNING: S3 upload failed, local copy preserved"
          fi
        fi

        # Record metadata (Req 28.3)
        record_metadata "$backup_id" "$timestamp" "$backup_file" "$size_bytes" "success"

        # Cleanup old backups beyond retention (Req 28.2)
        cleanup_old_backups

        return 0
      else
        log "Backup attempt ${attempt} produced empty file, removing..."
        rm -f "$backup_file"
      fi
    else
      log "Backup attempt ${attempt} failed"
      rm -f "$backup_file"
    fi

    # Wait before retry (exponential backoff: 10s, 20s, 40s)
    if [ $attempt -lt $MAX_RETRIES ]; then
      local wait_seconds=$((10 * (2 ** (attempt - 1))))
      log "Waiting ${wait_seconds}s before retry..."
      sleep $wait_seconds
    fi
  done

  # All retries exhausted (Req 28.4: alert Admin)
  record_metadata "$backup_id" "$timestamp" "" "0" "failed"
  send_alert "Automated backup FAILED after ${MAX_RETRIES} attempts for database=${MYSQL_DATABASE}. Backup id=${backup_id}. Most recent successful backup is preserved." "critical"
  return 1
}

# ---------------------------------------------------------------------------
# Retention cleanup (Req 28.2: retain ≥30 days)
# ---------------------------------------------------------------------------
cleanup_old_backups() {
  log "Cleaning up backups older than ${RETENTION_DAYS} days..."
  find "$BACKUP_DIR" -name "${MYSQL_DATABASE}_*.sql.gz" -type f -mtime +"$RETENTION_DAYS" -exec rm -f {} \;

  # If using S3, also cleanup old objects
  if [ -n "$S3_BUCKET" ]; then
    local cutoff_date
    cutoff_date=$(date -u -d "${RETENTION_DAYS} days ago" '+%Y-%m-%d' 2>/dev/null || date -u -v-"${RETENTION_DAYS}"d '+%Y-%m-%d' 2>/dev/null)
    if [ -n "$cutoff_date" ]; then
      local s3_args=""
      if [ -n "$S3_ENDPOINT" ]; then
        s3_args="--endpoint-url $S3_ENDPOINT"
      fi
      log "S3 retention cleanup: removing objects older than ${cutoff_date}"
      # List and remove old objects (best effort)
      # shellcheck disable=SC2086
      aws s3 ls "${S3_BUCKET}/" $s3_args 2>/dev/null | while read -r line; do
        local obj_date
        obj_date=$(echo "$line" | awk '{print $1}')
        if [[ "$obj_date" < "$cutoff_date" ]]; then
          local obj_name
          obj_name=$(echo "$line" | awk '{print $4}')
          if [ -n "$obj_name" ]; then
            # shellcheck disable=SC2086
            aws s3 rm "${S3_BUCKET}/${obj_name}" $s3_args 2>/dev/null || true
          fi
        fi
      done
    fi
  fi
}

# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------
perform_backup
