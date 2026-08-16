# Idempotent Discord bot launcher. Used by start-all.ps1 and the dashboard.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Test-GlyphDiscordRunning {
    $match = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object {
            $_.CommandLine -and ($_.CommandLine -match "glyph-discord-\S+\.jar|GlyphDiscordMain")
        }
    return [bool]$match
}

if (Test-GlyphDiscordRunning) {
    Write-Output "Glyph Discord already running - skipping"
    return
}

& (Join-Path $root "glyph-discord\start.ps1") -Hidden
Write-Output "Started glyph-discord"
