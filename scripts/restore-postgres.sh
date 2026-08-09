#!/usr/bin/env sh
set -eu

input_file="${1:?usage: restore-postgres.sh /absolute/path/backup.dump}"
: "${PIS_DB_HOST:?PIS_DB_HOST must be set}"
: "${PIS_DB_NAME:?PIS_DB_NAME must be set}"
: "${PIS_DB_USER:?PIS_DB_USER must be set}"
: "${PIS_DB_PASSWORD:?PIS_DB_PASSWORD must be set}"

if [ "${PIS_RESTORE_CONFIRM:-}" != "RESTORE-${PIS_DB_NAME}" ]; then
  echo "restore blocked: set PIS_RESTORE_CONFIRM=RESTORE-${PIS_DB_NAME} after confirming the target" >&2
  exit 2
fi
case "$input_file" in
  /*) ;;
  *)
    echo "an absolute backup path and matching .sha256 file are required" >&2
    exit 2
    ;;
esac
if [ ! -f "$input_file" ] || [ ! -f "$input_file.sha256" ]; then
  echo "an absolute backup path and matching .sha256 file are required" >&2
  exit 2
fi

(
  cd "$(dirname -- "$input_file")"
  sha256sum -c "$(basename -- "$input_file").sha256"
)
PGPASSWORD="$PIS_DB_PASSWORD" pg_restore \
  --host="$PIS_DB_HOST" \
  --port="${PIS_DB_PORT:-5432}" \
  --username="$PIS_DB_USER" \
  --dbname="$PIS_DB_NAME" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --exit-on-error \
  --single-transaction \
  "$input_file"
echo "restore completed for database: $PIS_DB_NAME"
