# One-time move to a new host (docs/MINI_PC.md), step 2 of 2: run on the NEW
# machine from a fresh clone, with Docker Desktop running. Restores the bundle
# written by scripts\migrate-export.ps1: files back into the repo, Cloudflare
# tunnel credentials, Glyph user env vars, then the PostgreSQL dump.
#
#   .\migrate-import.ps1 -Source E:\glyph-migration
#
# Refuses to restore over a database that already has tables (a server was
# started here first and Flyway ran). -ResetDatabase drops the Glyph data
# volume and restores into a fresh one.

param(
    [Parameter(Mandatory = $true)][string]$Source,
    [switch]$ResetDatabase
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

$dockerBin = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin"
if (Test-Path $dockerBin) { $env:PATH = "$dockerBin;" + $env:PATH }

$dbFile = Join-Path $Source "glyph-db.dump"
if (-not (Test-Path (Join-Path $Source "repo")) -or -not (Test-Path $dbFile)) {
    Write-Error "$Source is not a migrate-export.ps1 bundle (needs repo\ and glyph-db.dump)."
}

# --- 1. Files ---------------------------------------------------------------
Write-Output "Copying worlds, plugin data, jars and secrets into $root..."
robocopy (Join-Path $Source "repo") $root /E /R:1 /W:1 /NFL /NDL /NJH /NJS | Out-Null
if ($LASTEXITCODE -ge 8) { Write-Error "robocopy into the repo failed (exit $LASTEXITCODE)" }

$cfBundle = Join-Path $Source "cloudflared"
if (Test-Path $cfBundle) {
    robocopy $cfBundle (Join-Path $env:USERPROFILE ".cloudflared") /E /R:1 /W:1 /NFL /NDL /NJH /NJS | Out-Null
    Write-Output "Restored ~\.cloudflared (run scripts\setup-bluemap-tunnel.ps1 to install the service)"
}

$envFile = Join-Path $Source "user-env.json"
if (Test-Path $envFile) {
    $userEnv = Get-Content $envFile -Raw | ConvertFrom-Json
    foreach ($prop in $userEnv.PSObject.Properties) {
        [Environment]::SetEnvironmentVariable($prop.Name, $prop.Value, "User")
        Write-Output "Set user env var $($prop.Name)"
    }
}

# --- 2. Database --------------------------------------------------------------
if ($ResetDatabase) {
    docker compose -f "$root\docker-compose.dev.yml" --env-file "$root\.env" down
    try { docker volume rm glyph-dev_glyph-postgres-data 2>$null | Out-Null } catch {}
    Write-Output "Dropped the Glyph PostgreSQL volume"
}

& "$PSScriptRoot\dev-up.ps1"

$container = "glyph-dev-postgres"
$deadline = (Get-Date).AddMinutes(2)
$ready = $false
do {
    Start-Sleep -Seconds 2
    try { docker exec $container pg_isready -U glyph_app -d glyph 2>$null | Out-Null; $ready = $LASTEXITCODE -eq 0 } catch {}
} until ($ready -or (Get-Date) -gt $deadline)
if (-not $ready) { Write-Error "PostgreSQL did not become ready - check 'docker ps' and that port 5432 is free." }

$tables = docker exec $container psql -U glyph_app -d glyph -tAc `
    "select count(*) from information_schema.tables where table_schema = 'public'"
if ([int]"$tables".Trim() -gt 0) {
    Write-Error "Database already has $("$tables".Trim()) tables. Re-run with -ResetDatabase to replace it with the dump."
}

docker cp $dbFile "${container}:/tmp/glyph-migrate.dump" | Out-Null
docker exec $container pg_restore -U glyph_app -d glyph --no-owner /tmp/glyph-migrate.dump
if ($LASTEXITCODE -ne 0) { Write-Error "pg_restore failed (exit $LASTEXITCODE)" }
docker exec $container rm /tmp/glyph-migrate.dump
Write-Output "Database restored."

Write-Output ""
Write-Output "Next (docs/MINI_PC.md): scripts\register-tasks.ps1, setup-firewall.ps1 and"
Write-Output "setup-bluemap-tunnel.ps1 as Administrator, then scripts\start-all.ps1."
