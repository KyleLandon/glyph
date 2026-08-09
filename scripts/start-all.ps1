# Brings up the full Glyph stack on the host desktop: Docker engine,
# PostgreSQL + Redis (dev-up.ps1), Folia backend and Velocity proxy.
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
    Start-Process powershell `
        -ArgumentList "-ExecutionPolicy", "Bypass", "-File", "$dir\start.ps1" `
        -WorkingDirectory $dir -WindowStyle Minimized
    Write-Output "Started $dir"
}

# playit.gg agent (CGNAT tunnel until the ISP hands over a public IP).
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

Start-ServerIfDown 25566 (Join-Path $root "glyph-folia")
Start-ServerIfDown 25565 (Join-Path $root "glyph-velocity")
