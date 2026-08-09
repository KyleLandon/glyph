@echo off
rem Double-clickable launcher for the Folia test server.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
pause
