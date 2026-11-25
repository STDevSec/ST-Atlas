#!/usr/bin/env bash
# ST Atlas restore — restores a database dump and (optionally) the storage archive
# produced by backup.sh. DESTRUCTIVE: it overwrites the current database.
#
#   ./scripts/restore.sh DB_DUMP.sql.gz [STORAGE.tar.gz]
#
set -euo pipefail

cd "$(dirname "$0")/.."
DB_FILE="${1:?usage: restore.sh DB_DUMP.sql.gz [STORAGE.tar.gz]}"
STORAGE_FILE="${2:-}"
[ -f "$DB_FILE" ] || { echo "error: $DB_FILE not found" >&2; exit 1; }

dc() { docker compose "$@"; }

if [ -z "$(dc ps -q db 2>/dev/null)" ]; then
  echo "error: the 'db' service is not running. Start the stack first (docker compose up -d)." >&2
  exit 1
fi

echo "This will OVERWRITE the current ST Atlas database with:"
echo "  db:      $DB_FILE"
[ -n "$STORAGE_FILE" ] && echo "  storage: $STORAGE_FILE"
read -r -p "Type 'restore' to continue: " confirm
[ "$confirm" = "restore" ] || { echo "aborted."; exit 1; }

echo "→ Restoring database"
gunzip -c "$DB_FILE" \
  | dc exec -T db sh -c 'PGPASSWORD="$(cat /run/atlas-secrets/ATLAS_DB_PASSWORD)" psql -v ON_ERROR_STOP=1 -U atlas -d atlas'

if [ -n "$STORAGE_FILE" ]; then
  [ -f "$STORAGE_FILE" ] || { echo "error: $STORAGE_FILE not found" >&2; exit 1; }
  echo "→ Restoring storage volume"
  # Replace the contents of /data with the archive.
  dc exec -T backend sh -c 'rm -rf /data/* /data/.[!.]* 2>/dev/null; tar -xzf - -C /data' < "$STORAGE_FILE"
fi

echo "✓ Restore complete. Restarting backend to pick up restored state…"
dc restart backend >/dev/null
echo "Done."
