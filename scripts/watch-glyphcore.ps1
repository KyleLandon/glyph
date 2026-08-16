# Rebuilds Glyph plugins when sources change. Does NOT restart Folia unless
# you pass -Restart (that kicks the in-game 10s countdown).
#
# Usage:
#   scripts\watch-glyphcore.ps1
#   scripts\watch-glyphcore.ps1 -Restart
param(
    [switch]$Restart,
    [int]$DebounceSeconds = 3
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$builder = Join-Path $PSScriptRoot "build_glyphcore.ps1"

$watchRoots = @(
    (Join-Path $root "glyph-core\src"),
    (Join-Path $root "glyph-api\src"),
    (Join-Path $root "glyph-proxy\src")
) | Where-Object { Test-Path $_ }

if (-not $watchRoots) {
    Write-Error "No source trees to watch."
}

function Get-NewestSource {
    Get-ChildItem $watchRoots -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in ".java", ".yml", ".json", ".sql" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

$lastStamp = Get-Date
Write-Host "Watching plugin sources. Ctrl+C to stop." -ForegroundColor Green
Write-Host "Apply a staged jar with /restart  (or start this watcher with -Restart)." -ForegroundColor DarkGray

while ($true) {
    Start-Sleep -Milliseconds 800
    $newest = Get-NewestSource
    if (-not $newest) { continue }
    if ($newest.LastWriteTime -le $lastStamp) { continue }
    if ((Get-Date) -lt $newest.LastWriteTime.AddSeconds($DebounceSeconds)) { continue }

    Write-Host ("[{0}] building after {1}" -f (Get-Date -Format "HH:mm:ss"), $newest.Name) -ForegroundColor Cyan
    if ($Restart) {
        & $builder -Restart
    } else {
        & $builder
    }
    $code = $LASTEXITCODE
    $lastStamp = Get-Date
    if ($code -ne 0) {
        Write-Host "Build failed (exit $code). Watcher still running." -ForegroundColor Red
    }
}
