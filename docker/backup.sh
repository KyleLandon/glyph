#!/usr/bin/env bash
# Glyph production backup (GDD Phase 7, section 105).
#
# Runs on the docker host: dumps PostgreSQL and archives the Folia data
# volume (world + plugin state). Schedule with cron, e.g.
#   30 4 * * * /opt/glyph/docker/backup.sh >> /var/log/glyph-backup.log 2>&1
#
# Restore:
#   pg_restore:  docker exec -i glyph-postgres pg_restore -U glyph_app \
#                  -d glyph --clean --if-exists < glyph-db-<stamp>.dump
#   world:       stop folia, extract the tar into the glyph-folia-data volume.

set -euo pipefail

DESTINATION="${GLYPH_BACKUP_DIR:-/var/backups/glyph}"
RETENTION_DAYS="${GLYPH_BACKUP_RETENTION_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$DESTINATION"

# --- 1. PostgreSQL dump (custom format: compressed, pg_restore-able) -------
docker exec glyph-postgres pg_dump -U glyph_app -Fc glyph \
    > "$DESTINATION/glyph-db-$STAMP.dump"
echo "Database: $DESTINATION/glyph-db-$STAMP.dump"

# --- 2. Folia data volume (world, plugins, configs) -------------------------
# A throwaway container mounts the volume read-only and tars it. Live region
# files are almost always recoverable; stop the stack first for gold-standard
# backups before risky changes.
docker run --rm \
    -v glyph_glyph-folia-data:/data:ro \
    -v "$DESTINATION":/backup \
    alpine tar czf "/backup/glyph-folia-data-$STAMP.tar.gz" -C /data .
echo "Folia:    $DESTINATION/glyph-folia-data-$STAMP.tar.gz"

# --- 3. Retention ------------------------------------------------------------
find "$DESTINATION" -maxdepth 1 -name 'glyph-*' -type f \
    -mtime "+$RETENTION_DAYS" -print -delete

echo "Backup complete."
