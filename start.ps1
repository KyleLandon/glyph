# Double-click start.bat (this file is what PowerShell actually runs).
# Opens the local ops page and brings the stack up hidden if anything is down.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
& (Join-Path $PSScriptRoot "scripts\dashboard.ps1")
