#!/bin/bash
# PostgreSQL backup script — run daily at 2 AM
# Keeps last 7 days of backups

set -euo pipefail

BACKUP_DIR="/opt/parandiplaan/backups/postgres"
CONTAINER="pp-postgres"
DB_NAME="${POSTGRES_DB:-parandiplaan}"
DB_USER="${POSTGRES_USER:-ppuser}"
RETENTION_DAYS=7
DATE=$(date +%Y-%m-%d_%H%M)

mkdir -p "$BACKUP_DIR"

echo "[$(date)] Starting PostgreSQL backup..."

docker exec "$CONTAINER" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_DIR/pg_backup_${DATE}.sql.gz"

echo "[$(date)] Backup created: pg_backup_${DATE}.sql.gz ($(du -h "$BACKUP_DIR/pg_backup_${DATE}.sql.gz" | cut -f1))"

# Remove backups older than retention period
find "$BACKUP_DIR" -name "pg_backup_*.sql.gz" -mtime +${RETENTION_DAYS} -delete

echo "[$(date)] Cleanup complete. Remaining backups:"
ls -lh "$BACKUP_DIR"/pg_backup_*.sql.gz 2>/dev/null || echo "  (none)"
