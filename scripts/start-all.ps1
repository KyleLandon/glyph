# Brings up the full Glyph stack on the host desktop: Docker engine,
# PostgreSQL + Redis (dev-up.ps1), Folia anarchy, Paper SMP, Velocity, Discord.
# Registered as the "Glyph Servers" logon scheduled task so the network
# survives a reboot unattended (docs/PUBLIC_ACCESS.md). Idempotent: anything
# already running is left alone.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent

# Docker Desktop installs per-user and is not on PATH in a scheduled-task
# context, so resolve the CLI explicitly.
$dockerDir = "$env:LOCALAPPDATA\Programs\DockerDesktop"
if (Test-Path "$dockerDir\resources\bin") {
    $env:PATH = "$dockerDir\resources\bin;" + $env:PATH
}

$engineUp = $false
try { if (docker version --format "{{.Server.Version}}" 2>$null) { $engineUp = $true } } catch {}
if (-not $engineUp) {
    Start-Process "$dockerDir\Docker Desktop.exe"
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        try { if (docker version --format "{{.Server.Version}}" 2>$null) { $engineUp = $true; break } } catch {}
    }
    if (-not $engineUp) { Write-Error "Docker engine did not come up within 5 minutes." }
}

& "$PSScriptRoot\dev-up.ps1"

function Start-ServerIfDown([int]$port, [string]$dir) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        Write-Output "Port $port already listening - skipping $dir"
        return
    }
    & (Join-Path $dir "start.ps1") -Hidden
    Write-Output "Started $dir"
}

# playit.gg only while CGNAT forces a tunnel (GLYPH_USE_PLAYIT=1).
if ($env:GLYPH_USE_PLAYIT -eq "1") {
    $playit = "C:\Program Files\playit_gg\bin\playit.exe"
    if (Test-Path $playit) {
        $status = & $playit status 2>$null
        if ("$status" -notmatch "Phase: running") {
            & $playit start 2>$null | Out-Null
            Write-Output "Started playitd"
        } else {
            Write-Output "playitd already running"
        }
    }
}

Start-ServerIfDown 25566 (Join-Path $root "glyph-folia")
$smpDir = Join-Path $root "glyph-smp"
$smpJar = Get-ChildItem -Path $smpDir -Filter "paper-*.jar" -File -ErrorAction SilentlyContinue
if ($smpJar) {
    Start-ServerIfDown 25567 $smpDir
} else {
    Write-Output "No paper-*.jar in glyph-smp - run scripts\setup-smp.ps1"
}
Start-ServerIfDown 25565 (Join-Path $root "glyph-velocity")
& "$PSScriptRoot\start-discord.ps1"
