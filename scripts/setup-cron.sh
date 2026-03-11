#!/bin/bash
# Install backup cron jobs on the production server
# Run once: bash /opt/parandiplaan/scripts/setup-cron.sh

set -euo pipefail

SCRIPT_DIR="/opt/parandiplaan/scripts"

# Make scripts executable
chmod +x "$SCRIPT_DIR/backup-postgres.sh"
chmod +x "$SCRIPT_DIR/backup-minio.sh"
chmod +x "$SCRIPT_DIR/backup-volumes.sh"

# Create backup directories
mkdir -p /opt/parandiplaan/backups/{postgres,minio,volumes}

# Install crontab entries (preserving existing cron jobs)
(crontab -l 2>/dev/null | grep -v "backup-postgres\|backup-minio\|backup-volumes"; cat <<EOF
# Pärandiplaan backups
0 2 * * * $SCRIPT_DIR/backup-postgres.sh >> /var/log/pp-backup.log 2>&1
0 3 * * * $SCRIPT_DIR/backup-minio.sh >> /var/log/pp-backup.log 2>&1
0 4 * * 0 $SCRIPT_DIR/backup-volumes.sh >> /var/log/pp-backup.log 2>&1
EOF
) | crontab -

echo "Cron jobs installed:"
crontab -l | grep -A1 "Pärandiplaan"
echo ""
echo "Backup log: /var/log/pp-backup.log"
