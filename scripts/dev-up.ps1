# Starts local PostgreSQL + Redis for development.
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path "$root\.env")) {
    Copy-Item "$root\.env.example" "$root\.env"
    Write-Host "Created .env from .env.example - review the passwords." -ForegroundColor Yellow
}
docker compose -f "$root\docker-compose.dev.yml" --env-file "$root\.env" up -d
docker compose -f "$root\docker-compose.dev.yml" ps
