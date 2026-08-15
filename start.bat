@echo off
rem Double-clickable launcher for the full Glyph network:
rem Docker, PostgreSQL, Redis, Folia, Velocity, and playit.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
pause
