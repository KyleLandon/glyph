@echo off
rem Opens the local Glyph ops page and brings the stack up if needed.
cd /d "%~dp0"
start "" powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dashboard.ps1"
