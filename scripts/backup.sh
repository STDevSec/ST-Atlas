#!/usr/bin/env bash
# ST Atlas backup — dumps the PostgreSQL database and the attachment/PDF storage volume.
# Runs against the compose stack in this repo. Output goes to ./backups by default.
#
#   ./scripts/backup.sh [OUTPUT_DIR]
#
# Honours COMPOSE_PROJECT_NAME and any COMPOSE_FILE overrides already in your env.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT_DIR="${1:-backups}"
TS="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT_DIR"

dc() { docker compose "$@"; }

if [ -z "$(dc ps -q db 2>/dev/null)" ]; then
  echo "error: the 'db' service is not running. Start the stack first (docker compose up -d)." >&2
  exit 1
fi

DB_FILE="$OUT_DIR/atlas-db-$TS.sql.gz"
STORAGE_FILE="$OUT_DIR/atlas-storage-$TS.tar.gz"

echo "→ Dumping database to $DB_FILE"
# The password lives in the Docker secret mounted inside the container; it never touches the host.
dc exec -T db sh -c 'PGPASSWORD="$(cat /run/atlas-secrets/ATLAS_DB_PASSWORD)" pg_dump -U atlas -d atlas --clean --if-exists' \
  | gzip > "$DB_FILE"

echo "→ Archiving storage volume to $STORAGE_FILE"
# tar the attachment/PDF tree straight out of the backend's /data mount.
dc exec -T backend sh -c 'tar -czf - -C /data .' > "$STORAGE_FILE"

echo "✓ Backup complete:"
ls -lh "$DB_FILE" "$STORAGE_FILE" | awk '{print "  " $5 "\t" $9}'
echo
echo "Restore with: ./scripts/restore.sh \"$DB_FILE\" \"$STORAGE_FILE\""
