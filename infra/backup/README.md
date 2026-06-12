# Backup and Recovery Infrastructure

Automated backup and restore system for the AI Software Architect Platform's MySQL database.

## Overview

This directory contains scripts and Kubernetes manifests that implement the Platform's backup and recovery requirements (Requirement 28.1–28.6).

## Components

| File | Purpose |
|------|---------|
| `backup.sh` | Automated backup script with retry logic and alerting |
| `restore.sh` | Restore script with abort-on-failure safety |
| `k8s-backup-cronjob.yaml` | Kubernetes CronJob + supporting resources |
| `backup-metadata.json` | JSON-lines log recording each backup (id + timestamp) |

## Policies

### Backup Policy (Req 28.1, 28.3, 28.4)

- **Interval:** Every 24 hours (CronJob at 02:00 UTC daily). Configurable but must not exceed 24h.
- **Method:** Full MySQL dump (`mysqldump --single-transaction`) compressed with gzip.
- **Identification:** Each backup is tagged with a UUID v4 identifier and a UTC timestamp.
- **Storage:** Local PVC (`/backups`) and optionally an S3-compatible bucket.
- **Retry:** On failure, retries up to 3 times with exponential backoff (10s, 20s, 40s).
- **Alert:** If all 3 retries are exhausted, an alert is sent to the Admin via webhook and logged.

### Retention Policy (Req 28.2)

- **Minimum retention:** 30 days. Configurable via `RETENTION_DAYS` env var (enforced ≥30).
- **Cleanup:** After each successful backup, files older than the retention period are deleted.
- **Applies to:** Both local storage and S3 objects.

### Restore SLA (Req 28.5, 28.6)

- **Target:** Restore completes within **60 minutes** of initiation.
- **Timeout:** Enforced via `timeout` command (default: 60 minutes, configurable via `RESTORE_TIMEOUT`).
- **Safety:** Before restoring, a pre-restore snapshot is taken of the current database state.
- **Abort behavior:** If restore fails, the operation is aborted and the pre-restore data is preserved unchanged.
- **Alert:** On failure, an alert is sent to the Admin with details of the failure.

## Usage

### Manual Backup (local development)

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_USER=root
export MYSQL_PASSWORD=rootpw
export MYSQL_DATABASE=aisa
export BACKUP_DIR=./backups

./backup.sh
```

### Manual Restore

```bash
# Restore by backup ID (looks up in BACKUP_DIR and/or S3)
./restore.sh <backup-uuid>

# Restore by file path
./restore.sh --file /backups/aisa_abc123_20240101_020000.sql.gz
```

### Kubernetes Deployment

```bash
# Create namespace
kubectl create namespace aisa

# Create secrets
kubectl create secret generic aisa-mysql-credentials \
  --from-literal=username=root \
  --from-literal=password=<mysql-password> \
  -n aisa

kubectl create secret generic aisa-alert-config \
  --from-literal=webhook-url=<alert-webhook-url> \
  -n aisa

# Create ConfigMap with actual scripts
kubectl create configmap aisa-backup-scripts \
  --from-file=backup.sh=infra/backup/backup.sh \
  --from-file=restore.sh=infra/backup/restore.sh \
  -n aisa

# Apply CronJob and supporting resources
kubectl apply -f infra/backup/k8s-backup-cronjob.yaml
```

### Trigger a Manual Backup in Kubernetes

```bash
kubectl create job --from=cronjob/aisa-mysql-backup manual-backup-$(date +%s) -n aisa
```

### Trigger a Restore in Kubernetes

```bash
kubectl run aisa-restore --rm -it \
  --image=mysql:8.4 \
  --env="MYSQL_HOST=aisa-mysql" \
  --env="MYSQL_PORT=3306" \
  --env="MYSQL_USER=root" \
  --env="MYSQL_PASSWORD=<password>" \
  --env="MYSQL_DATABASE=aisa" \
  --env="BACKUP_DIR=/backups" \
  --overrides='{"spec":{"containers":[{"name":"aisa-restore","volumeMounts":[{"name":"backup-storage","mountPath":"/backups"},{"name":"backup-scripts","mountPath":"/scripts"}]}],"volumes":[{"name":"backup-storage","persistentVolumeClaim":{"claimName":"aisa-backup-pvc"}},{"name":"backup-scripts","configMap":{"name":"aisa-backup-scripts","defaultMode":493}}]}}' \
  -n aisa \
  -- /bin/bash /scripts/restore.sh <backup-id>
```

## Backup Metadata Log

Each backup (successful or failed) is appended to `backup-metadata.json` as a JSON-lines entry:

```json
{"id":"550e8400-e29b-41d4-a716-446655440000","timestamp":"2024-01-15T02:00:00.000Z","file":"/backups/aisa_550e8400_20240115_020000.sql.gz","sizeBytes":15728640,"status":"success","database":"aisa","retentionDays":30}
```

Fields:
- `id` — Unique backup identifier (UUID v4)
- `timestamp` — UTC timestamp of backup initiation
- `file` — Path to the backup file (empty string if failed)
- `sizeBytes` — Size of the backup file in bytes (0 if failed)
- `status` — `success` or `failed`
- `database` — Database name
- `retentionDays` — Configured retention period

## Monitoring

The backup CronJob can be monitored via:
- **Kubernetes:** `kubectl get jobs -n aisa -l app.kubernetes.io/component=backup`
- **Alerts:** Failed backups trigger Admin alerts via the configured webhook
- **Grafana:** Import a Kubernetes CronJob dashboard to track execution history
- **Metadata log:** Parse `backup-metadata.json` for audit and reporting

## Requirements Traceability

| Requirement | Implementation |
|-------------|---------------|
| 28.1 — Automated backups ≤24h | CronJob `schedule: "0 2 * * *"` |
| 28.2 — Retention ≥30 days | `RETENTION_DAYS` env var (enforced ≥30), cleanup in `backup.sh` |
| 28.3 — Record backup id + timestamp | `backup-metadata.json` JSON-lines log |
| 28.4 — Retry 3x, alert Admin on failure | `MAX_RETRIES=3` in `backup.sh`, webhook + log alert |
| 28.5 — Restore ≤60 minutes | `RESTORE_TIMEOUT=60` in `restore.sh`, enforced via `timeout` |
| 28.6 — Abort + preserve on failure, alert | Pre-restore snapshot + rollback + alert in `restore.sh` |
