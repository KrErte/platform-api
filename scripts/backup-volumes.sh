#!/bin/bash
# Docker volume backup script — weekly full backup
# Run weekly at 4 AM on Sundays

set -euo pipefail

BACKUP_DIR="/opt/parandiplaan/backups/volumes"
DATE=$(date +%Y-%m-%d_%H%M)
VOLUMES=("pp-postgres-data" "pp-minio-data" "pp-caddy-data" "pp-caddy-config")
RETENTION_DAYS=30

mkdir -p "$BACKUP_DIR"

echo "[$(date)] Starting Docker volume backup..."

for vol in "${VOLUMES[@]}"; do
    DEST="$BACKUP_DIR/${vol}_${DATE}.tar.gz"
    docker run --rm -v "${vol}:/data" -v "$BACKUP_DIR:/backup" alpine \
        tar czf "/backup/${vol}_${DATE}.tar.gz" -C /data .
    echo "  Backed up $vol ($(du -h "$DEST" | cut -f1))"
done

# Remove old backups
find "$BACKUP_DIR" -name "*.tar.gz" -mtime +${RETENTION_DAYS} -delete

echo "[$(date)] Volume backup complete."
