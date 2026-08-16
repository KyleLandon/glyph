@echo off
rem Invoked by /restart. Wait until the dying Paper releases :25567, then
rem start java hidden so the dashboard can keep tailing logs.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$deadline = (Get-Date).AddMinutes(2); " ^
  "while ((Get-Date) -lt $deadline) { " ^
  "  if (-not (Get-NetTCPConnection -LocalPort 25567 -State Listen -ErrorAction SilentlyContinue)) { break }; " ^
  "  Start-Sleep -Seconds 1 " ^
  "}"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" -Hidden
