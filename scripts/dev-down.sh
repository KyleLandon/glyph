#!/usr/bin/env bash
# Stops local development infrastructure. Pass --wipe to destroy data volumes.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
if [[ "${1:-}" == "--wipe" ]]; then
  docker compose -f "$root/docker-compose.dev.yml" down -v
else
  docker compose -f "$root/docker-compose.dev.yml" down
fi
