#!/bin/bash
# MinIO backup script — mirrors vault bucket to local backup dir
# Run daily at 3 AM

set -euo pipefail

BACKUP_DIR="/opt/parandiplaan/backups/minio"
MINIO_ALIAS="pp"
BUCKET="${MINIO_BUCKET:-parandiplaan-vault}"
DATE=$(date +%Y-%m-%d_%H%M)

mkdir -p "$BACKUP_DIR"

echo "[$(date)] Starting MinIO backup..."

# Configure mc alias if not already set
if ! docker exec pp-minio mc alias list "$MINIO_ALIAS" &>/dev/null; then
    docker exec pp-minio mc alias set "$MINIO_ALIAS" http://localhost:9000 "${MINIO_ACCESS_KEY:-minioadmin}" "${MINIO_SECRET_KEY:-minioadmin}"
fi

# Mirror bucket to backup directory via host mount
docker exec pp-minio mc mirror --overwrite "${MINIO_ALIAS}/${BUCKET}" "/backup/minio/${DATE}"

echo "[$(date)] MinIO backup complete: $BACKUP_DIR/$DATE"

# Remove backups older than 7 days
find "$BACKUP_DIR" -maxdepth 1 -type d -mtime +7 -exec rm -rf {} + 2>/dev/null || true

echo "[$(date)] Cleanup complete."
