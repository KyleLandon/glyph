# Pull origin/main onto this machine when the laptop has pushed.
# The desktop is behind CGNAT (no inbound SSH/webhooks), so this polls
# GitHub on a schedule instead of being triggered by the push itself.
#
# Usage (run once on the desktop):
#   .\auto-pull.ps1 -Register
#
# Then leave it. Every 2 minutes it fetches; if main moved and the tree is
# clean, it fast-forwards. Uncommitted local edits are left alone.
#
# This does not rebuild plugins or restart Folia/Velocity. After a pull that
# touches GlyphCore, run scripts\build_glyphcore.ps1 and restart the servers.

param(
    [switch]$Register,
    [int]$IntervalMinutes = 2
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$logDir = Join-Path $root "logs"
$logFile = Join-Path $logDir "auto-pull.log"

function Write-Log([string]$message) {
    $line = "{0}  {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $message
    Write-Output $line
    if (-not (Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }
    Add-Content -LiteralPath $logFile -Value $line
}

if ($Register) {
    $action = New-ScheduledTaskAction -Execute "powershell.exe" `
        -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    $start = (Get-Date).AddMinutes(1)
    $trigger = New-ScheduledTaskTrigger -Once -At $start `
        -RepetitionInterval (New-TimeSpan -Minutes $IntervalMinutes) `
        -RepetitionDuration (New-TimeSpan -Days 3650)
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable `
        -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -ExecutionTimeLimit (New-TimeSpan -Minutes 5) `
        -MultipleInstances IgnoreNew
    Register-ScheduledTask -TaskName "Glyph Auto Pull" -Action $action `
        -Trigger $trigger -Settings $settings -Force | Out-Null
    Write-Output "Scheduled task 'Glyph Auto Pull' registered (every $IntervalMinutes min)."
    Write-Output "Needs GitHub credentials for this Windows user (Git Credential Manager)."
    return
}

Set-Location $root

$git = Get-Command git -ErrorAction SilentlyContinue
if (-not $git) {
    Write-Log "git not on PATH — skipped"
    exit 0
}

git rev-parse --is-inside-work-tree 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Log "not a git repo — skipped"
    exit 0
}

git fetch origin 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Log "git fetch failed (credentials or network) — skipped"
    exit 0
}

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne "main") {
    Write-Log "on $branch, not main — skipped"
    exit 0
}

$local = (git rev-parse HEAD).Trim()
$remote = (git rev-parse origin/main).Trim()
if ($local -eq $remote) {
    exit 0
}

git diff --quiet
$dirtyWorktree = $LASTEXITCODE -ne 0
git diff --cached --quiet
$dirtyIndex = $LASTEXITCODE -ne 0
if ($dirtyWorktree -or $dirtyIndex) {
    Write-Log "origin/main is ahead but this clone has uncommitted changes — skipped"
    exit 0
}

$before = $local
git pull --ff-only origin main 2>&1 | ForEach-Object { Write-Log $_ }
if ($LASTEXITCODE -ne 0) {
    Write-Log "git pull --ff-only failed"
    exit 1
}

$after = (git rev-parse --short HEAD).Trim()
$changed = git diff --name-only "$before" HEAD
Write-Log "updated $($before.Substring(0, 7)) -> $after"

$pluginTouch = $changed | Where-Object {
    $_ -like "glyph-core/*" -or $_ -like "glyph-proxy/*" -or $_ -like "glyph-api/*" -or
    $_ -like "glyph-folia/plugins/*" -or $_ -like "glyph-smp/plugins/*" -or
    $_ -like "glyph-velocity/plugins/*"
}
if ($pluginTouch) {
    Write-Log "plugin files changed - run scripts\build_glyphcore.ps1 and restart Folia/Velocity to apply"
}
