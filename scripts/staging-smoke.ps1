# Staging smoke suite (GDD Phase 8).
# Checks stack health, runs concurrency/outage integration tests, exercises
# a live database pause and a backend kill/restart. Leaves the stack up.
#
# Usage:
#   .\staging-smoke.ps1
#   .\staging-smoke.ps1 -SkipTests          # ops checks only
#   .\staging-smoke.ps1 -SkipRestart        # keep the current backend process

param(
    [switch]$SkipTests,
    [switch]$SkipRestart,
    [switch]$SkipOutage
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$failed = 0
$passed = 0

function Ok([string]$msg) { Write-Output "  PASS  $msg"; $script:passed++ }
function Bad([string]$msg) { Write-Output "  FAIL  $msg"; $script:failed++ }
function Step([string]$msg) { Write-Output ""; Write-Output "==> $msg" }

# Docker Desktop is per-user and may not be on PATH in some shells.
$dockerBin = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin"
if (Test-Path $dockerBin) { $env:PATH = "$dockerBin;" + $env:PATH }

# --- 1. Infrastructure -------------------------------------------------------
Step "Infrastructure"
& "$PSScriptRoot\dev-up.ps1" | Out-Null

$pg = docker inspect -f "{{.State.Health.Status}}" glyph-dev-postgres 2>$null
$rd = docker inspect -f "{{.State.Health.Status}}" glyph-dev-redis 2>$null
if ($pg -eq "healthy") { Ok "postgres healthy" } else { Bad "postgres status: $pg" }
if ($rd -eq "healthy") { Ok "redis healthy" } else { Bad "redis status: $rd" }

# --- 2. Proxy + backend listening --------------------------------------------
Step "Proxy and backend ports"
function Ensure-Listening([int]$port, [string]$dir) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        return $true
    }
    Start-Process powershell `
        -ArgumentList "-ExecutionPolicy", "Bypass", "-File", "$dir\start.ps1" `
        -WorkingDirectory $dir -WindowStyle Minimized
    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            return $true
        }
    }
    return $false
}

if (Ensure-Listening 25566 (Join-Path $root "glyph-folia")) {
    Ok "folia listening on 25566"
} else { Bad "folia failed to come up on 25566" }

if (Ensure-Listening 25565 (Join-Path $root "glyph-velocity")) {
    Ok "velocity listening on 25565"
} else { Bad "velocity failed to come up on 25565" }

# Wait for GlyphCore "Database ready" if the backend just started.
$log = Join-Path $root "glyph-folia\logs\latest.log"
$readyDeadline = (Get-Date).AddMinutes(2)
$coreReady = $false
while ((Get-Date) -lt $readyDeadline) {
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern "Database ready" -Quiet)) {
        $coreReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if ($coreReady) { Ok "GlyphCore database ready" } else { Bad "GlyphCore never reported Database ready" }

# RCON (used by later world scripts; soft-fail if not enabled yet).
try {
    $list = & "$PSScriptRoot\rcon.ps1" "list" 2>&1
    if ($LASTEXITCODE -eq 0 -or "$list" -match "players") {
        Ok "RCON responds ($list)"
    } else {
        Bad "RCON unexpected response: $list"
    }
} catch {
    Bad "RCON unavailable: $($_.Exception.Message)"
}

# --- 3. Integration tests (concurrency + outage semantics) -------------------
if (-not $SkipTests) {
    Step "Gradle integration tests"
    Push-Location $root
    try {
        & .\gradlew.bat :glyph-core:test --console=plain
        if ($LASTEXITCODE -eq 0) { Ok "glyph-core test suite" }
        else { Bad "glyph-core test suite exited $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
} else {
    Step "Gradle tests skipped"
}

# --- 4. Live database outage -------------------------------------------------
if (-not $SkipOutage) {
    Step "Live database outage (pause 8s)"
    docker pause glyph-dev-postgres | Out-Null
    Start-Sleep -Seconds 8
    # Backend must still be listening — no crash loop.
    if (Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue) {
        Ok "backend stayed up during DB pause"
    } else {
        Bad "backend dropped during DB pause"
    }
    docker unpause glyph-dev-postgres | Out-Null
    $recoverDeadline = (Get-Date).AddSeconds(30)
    $recovered = $false
    while ((Get-Date) -lt $recoverDeadline) {
        $status = docker inspect -f "{{.State.Health.Status}}" glyph-dev-postgres 2>$null
        if ($status -eq "healthy") { $recovered = $true; break }
        Start-Sleep -Seconds 2
    }
    if ($recovered) { Ok "postgres healthy after unpause" }
    else { Bad "postgres did not recover after unpause" }
} else {
    Step "DB outage skipped"
}

# --- 5. Crash + restart ------------------------------------------------------
if (-not $SkipRestart) {
    Step "Backend kill + restart"
    $conn = Get-NetTCPConnection -LocalPort 25566 -State Listen -ErrorAction SilentlyContinue
    if ($conn) {
        Stop-Process -Id $conn[0].OwningProcess -Force
        Start-Sleep -Seconds 2
    }
    if (Ensure-Listening 25566 (Join-Path $root "glyph-folia")) {
        Ok "backend restarted and listening"
    } else {
        Bad "backend failed to restart"
    }
    $log = Join-Path $root "glyph-folia\logs\latest.log"
    $readyDeadline = (Get-Date).AddMinutes(2)
    $coreReady = $false
    while ((Get-Date) -lt $readyDeadline) {
        if ((Test-Path $log) -and (Select-String -Path $log -Pattern "Database ready" -Quiet)) {
            $coreReady = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if ($coreReady) { Ok "GlyphCore ready after restart" }
    else { Bad "GlyphCore not ready after restart" }
} else {
    Step "Restart skipped"
}

# --- Summary -----------------------------------------------------------------
Step "Summary"
Write-Output "Passed: $passed"
Write-Output "Failed: $failed"
if ($failed -gt 0) {
    Write-Output ""
    Write-Output "Staging smoke FAILED. See docs/STAGING.md for the checklist."
    exit 1
}
Write-Output ""
Write-Output "Automated staging checks passed."
Write-Output "Still manual (docs/STAGING.md): combat/deaths, voice, reconnect-with-players."
exit 0
