#!/bin/bash
# Database restore script for Pärandiplaan
# Usage: ./scripts/restore-db.sh /opt/parandiplaan/backups/parandiplaan_2026-03-10_030000.dump

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <backup-file>"
    echo "Example: $0 /opt/parandiplaan/backups/parandiplaan_2026-03-10_030000.dump"
    exit 1
fi

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "ERROR: Backup file not found: $BACKUP_FILE"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Load credentials from .env
if [ -f "$PROJECT_DIR/.env" ]; then
    # shellcheck disable=SC1091
    source "$PROJECT_DIR/.env"
fi

POSTGRES_DB="${POSTGRES_DB:-parandiplaan}"
POSTGRES_USER="${POSTGRES_USER:-ppuser}"

echo "=== Pärandiplaan Database Restore ==="
echo "Backup file: $BACKUP_FILE"
echo "Database: $POSTGRES_DB"
echo ""
echo "WARNING: This will replace all data in the database!"
read -rp "Continue? (y/N): " CONFIRM

if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    echo "Restore cancelled."
    exit 0
fi

echo "Stopping API container..."
docker stop pp-api 2>/dev/null || true

echo "Restoring database..."
if docker exec -i pp-postgres pg_restore \
    --clean \
    --if-exists \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" < "$BACKUP_FILE"; then
    echo "Restore successful!"
else
    echo "WARNING: pg_restore completed with warnings (this is usually normal for --clean --if-exists)"
fi

echo "Restarting API container..."
docker start pp-api

echo "Restore complete. Verify at: https://parandiplaan.ee/actuator/health"
