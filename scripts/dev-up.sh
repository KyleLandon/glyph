#!/usr/bin/env bash
# Starts local PostgreSQL + Redis for development.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
if [[ ! -f "$root/.env" ]]; then
  cp "$root/.env.example" "$root/.env"
  echo "Created .env from .env.example — review the passwords."
fi
docker compose -f "$root/docker-compose.dev.yml" --env-file "$root/.env" up -d
docker compose -f "$root/docker-compose.dev.yml" ps
