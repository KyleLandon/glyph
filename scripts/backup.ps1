# Glyph backup (GDD Phase 7, section 105): PostgreSQL dump + world archive.
#
# Made for the current desktop deployment (Docker dev database + glyph-folia
# running on the host). The containerized production stack uses
# docker/backup.sh instead.
#
# Usage:
#   .\backup.ps1                          # back up now to K:\glyph-backups
#   .\backup.ps1 -Destination D:\backups  # elsewhere
#   .\backup.ps1 -Register                # + create a daily 04:30 scheduled task
#
# Notes:
# - The world is copied while the server may be running. Region files are
#   almost always recoverable from a live copy, but a backup taken while the
#   server is stopped is the gold standard - take one before risky changes.
# - Retention: files older than -RetentionDays are deleted from Destination.

param(
    [string]$Destination = "K:\glyph-backups",
    [int]$RetentionDays = 14,
    [switch]$Register
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

if ($Register) {
    $action = New-ScheduledTaskAction -Execute "powershell.exe" `
        -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Destination `"$Destination`" -RetentionDays $RetentionDays"
    $trigger = New-ScheduledTaskTrigger -Daily -At 4:30am
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable `
        -ExecutionTimeLimit (New-TimeSpan -Hours 2)
    Register-ScheduledTask -TaskName "Glyph Backup" -Action $action `
        -Trigger $trigger -Settings $settings -Force | Out-Null
    Write-Output "Scheduled task 'Glyph Backup' registered (daily 04:30)."
    return
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
New-Item -ItemType Directory -Path $Destination -Force | Out-Null

# Docker Desktop is per-user and not on PATH in a scheduled-task context.
$dockerBin = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin"
if (Test-Path $dockerBin) { $env:PATH = "$dockerBin;" + $env:PATH }

# --- 1. PostgreSQL dump (custom format: compressed, pg_restore-able) -------
$container = @("glyph-dev-postgres", "glyph-postgres") |
    Where-Object { docker ps --format "{{.Names}}" 2>$null | Select-String -Quiet "^$_$" } |
    Select-Object -First 1
if (-not $container) {
    Write-Error "No running Glyph postgres container found - is Docker up?"
}

$dbFile = Join-Path $Destination "glyph-db-$stamp.dump"
docker exec $container pg_dump -U glyph_app -Fc -f /tmp/glyph-backup.dump glyph
if ($LASTEXITCODE -ne 0) { Write-Error "pg_dump failed (exit $LASTEXITCODE)" }
docker cp "${container}:/tmp/glyph-backup.dump" $dbFile | Out-Null
docker exec $container rm /tmp/glyph-backup.dump
Write-Output ("Database: {0} ({1:N1} MB)" -f $dbFile, ((Get-Item $dbFile).Length / 1MB))

# --- 2. World archive (live copy via robocopy, then zip) -------------------
$staging = Join-Path $env:TEMP "glyph-world-backup-$stamp"
$worlds = Get-ChildItem (Join-Path $root "glyph-folia") -Directory -Filter "world*"
foreach ($world in $worlds) {
    # session.lock is held exclusively by the running server and is useless
    # in a backup. Robocopy exit codes 0-7 mean success; 8+ are real failures.
    robocopy $world.FullName (Join-Path $staging $world.Name) /E /R:1 /W:1 /XF session.lock /NFL /NDL /NJH /NJS | Out-Null
    if ($LASTEXITCODE -ge 8) { Write-Error "robocopy failed for $($world.Name) (exit $LASTEXITCODE)" }
}

$worldFile = Join-Path $Destination "glyph-worlds-$stamp.zip"
Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $worldFile
Remove-Item $staging -Recurse -Force
Write-Output ("Worlds:   {0} ({1:N1} MB)" -f $worldFile, ((Get-Item $worldFile).Length / 1MB))

# --- 3. Retention -----------------------------------------------------------
$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem $Destination -File |
    Where-Object { $_.Name -like "glyph-*" -and $_.LastWriteTime -lt $cutoff } |
    ForEach-Object {
        Remove-Item $_.FullName
        Write-Output "Pruned:   $($_.Name)"
    }

Write-Output "Backup complete."
