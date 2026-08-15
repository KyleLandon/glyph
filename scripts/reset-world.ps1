# Deletes Folia world folders so the next boot generates a fresh map from
# server.properties level-seed. Does NOT touch PostgreSQL (balances / stats
# stay). Player inventories live in world/playerdata and will be cleared.
#
# Stop Folia first. Then, after the new world boots:
#   scripts\apply-world-settings.ps1
#
# Usage:
#   .\reset-world.ps1
#   .\reset-world.ps1 -Force   # skip the confirmation prompt

param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$folia = Join-Path $root "glyph-folia"

if (Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue) {
    Write-Error "Folia is still listening on :25566 - stop the backend first."
}

$worlds = @(
    (Join-Path $folia "world"),
    (Join-Path $folia "world_nether"),
    (Join-Path $folia "world_the_end")
)
$existing = $worlds | Where-Object { Test-Path $_ }

if (-not $Force) {
    $summary = if ($existing) { $existing -join "`n  " } else { "(none present)" }
    Write-Host "This will delete:"
    Write-Host "  $summary"
    Write-Host "Seed after reset: 6944174826991112"
    Write-Host "Spawn after apply-world-settings.ps1: -184 70 45"
    $answer = Read-Host "Type YES to continue"
    if ($answer -ne "YES") {
        Write-Host "Aborted."
        exit 1
    }
}

foreach ($dir in $existing) {
    Remove-Item -LiteralPath $dir -Recurse -Force
    Write-Host "Removed $dir"
}

Write-Host "World folders cleared. Start Folia, wait until it is joinable, then run:"
Write-Host "  scripts\apply-world-settings.ps1"
