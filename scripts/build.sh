#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

(
  cd "$repository_root/backend"
  ./mvnw -B clean verify
)

(
  cd "$repository_root/frontend"
  npm ci
  npm run format:check
  npm run lint
  npm run typecheck
  npm run test:unit -- --run
  npm run build
)
