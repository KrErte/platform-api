#!/bin/bash
# Database backup script for Pärandiplaan
# Runs pg_dump inside pp-postgres container, saves compressed dump, rotates after 7 days.
# Usage: Run via cron — 0 3 * * * /opt/parandiplaan/scripts/backup-db.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="$PROJECT_DIR/backups"
LOG_FILE="$BACKUP_DIR/backup.log"
RETENTION_DAYS=7

# Load credentials from .env
if [ -f "$PROJECT_DIR/.env" ]; then
    # shellcheck disable=SC1091
    source "$PROJECT_DIR/.env"
fi

POSTGRES_DB="${POSTGRES_DB:-parandiplaan}"
POSTGRES_USER="${POSTGRES_USER:-ppuser}"

TIMESTAMP=$(date +"%Y-%m-%d_%H%M%S")
BACKUP_FILE="$BACKUP_DIR/parandiplaan_${TIMESTAMP}.dump"

mkdir -p "$BACKUP_DIR"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log "Starting database backup..."

if docker exec pp-postgres pg_dump \
    --format=custom \
    --username="$POSTGRES_USER" \
    "$POSTGRES_DB" > "$BACKUP_FILE" 2>>"$LOG_FILE"; then

    SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    log "Backup successful: $BACKUP_FILE ($SIZE)"
else
    log "ERROR: Backup failed!"
    rm -f "$BACKUP_FILE"
    exit 1
fi

# Rotate old backups
DELETED=$(find "$BACKUP_DIR" -name "parandiplaan_*.dump" -mtime +"$RETENTION_DAYS" -delete -print | wc -l)
if [ "$DELETED" -gt 0 ]; then
    log "Rotated $DELETED backup(s) older than $RETENTION_DAYS days"
fi

log "Backup complete."
