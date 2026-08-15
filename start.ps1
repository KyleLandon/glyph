# Double-click start.bat (this file is what PowerShell actually runs).
# Brings up Docker, PostgreSQL + Redis, Folia, Velocity, and playit.
# Implementation lives in scripts/start-all.ps1 so the logon scheduled task
# and this launcher stay in sync.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
& "$PSScriptRoot\scripts\start-all.ps1"
Write-Host ""
Write-Host "Glyph is coming up. Connect to localhost or play.glyphmc.net" -ForegroundColor Green
Write-Host "Folia and Velocity open in minimized windows."
