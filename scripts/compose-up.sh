#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

: "${PIS_DB_PASSWORD:?PIS_DB_PASSWORD must be set for local Compose execution}"
docker compose up --build
