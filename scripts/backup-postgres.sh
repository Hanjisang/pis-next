#!/usr/bin/env sh
set -eu

output_file="${1:?usage: backup-postgres.sh /absolute/path/backup.dump}"
: "${PIS_DB_HOST:?PIS_DB_HOST must be set}"
: "${PIS_DB_NAME:?PIS_DB_NAME must be set}"
: "${PIS_DB_USER:?PIS_DB_USER must be set}"
: "${PIS_DB_PASSWORD:?PIS_DB_PASSWORD must be set}"

case "$output_file" in
  /*) ;;
  *)
    echo "backup output must be an absolute path" >&2
    exit 2
    ;;
esac
if [ -e "$output_file" ]; then
  echo "backup target already exists: $output_file" >&2
  exit 2
fi

mkdir -p -- "$(dirname -- "$output_file")"
PGPASSWORD="$PIS_DB_PASSWORD" pg_dump \
  --host="$PIS_DB_HOST" \
  --port="${PIS_DB_PORT:-5432}" \
  --username="$PIS_DB_USER" \
  --dbname="$PIS_DB_NAME" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="$output_file"

(
  cd "$(dirname -- "$output_file")"
  sha256sum "$(basename -- "$output_file")" > "$(basename -- "$output_file").sha256"
)
echo "backup created: $output_file"
