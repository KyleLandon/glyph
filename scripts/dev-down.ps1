# Stops local development infrastructure. Data volumes are preserved;
# add -Wipe to destroy them.
param([switch]$Wipe)

$root = Split-Path -Parent $PSScriptRoot
if ($Wipe) {
    docker compose -f "$root\docker-compose.dev.yml" down -v
} else {
    docker compose -f "$root\docker-compose.dev.yml" down
}
