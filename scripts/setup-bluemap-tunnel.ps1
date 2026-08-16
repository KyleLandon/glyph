# Public HTTPS for BlueMap: https://map.glyphmc.net -> http://127.0.0.1:8100
#
# Cloudflare Pages cannot host a live 3D map. The marketing site at
# https://glyphmc.net/map iframes this hostname. The tunnel is the HTTPS
# front door so we never port-forward 8100 and never orange-cloud Minecraft.
#
# One-time, on the desktop (Admin for the Windows service):
#   scripts\setup-bluemap-tunnel.ps1
#
# Do not add map.glyphmc.net to GLYPH_DNS_RECORD. The tunnel owns that CNAME.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$cfDir = Join-Path $env:USERPROFILE ".cloudflared"
$tunnelName = "glyph-map"
$hostname = "map.glyphmc.net"
$origin = "http://127.0.0.1:8100"
$webserverConf = Join-Path $root "glyph-smp\plugins\BlueMap\webserver.conf"

function Find-Cloudflared {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $guesses = @(
        "$env:ProgramFiles\cloudflared\cloudflared.exe",
        "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe",
        "$env:LOCALAPPDATA\Microsoft\WinGet\Links\cloudflared.exe"
    )
    foreach ($g in $guesses) {
        if (Test-Path $g) { return $g }
    }
    return $null
}

$cloudflared = Find-Cloudflared
if (-not $cloudflared) {
    Write-Host "Installing Cloudflare Tunnel (cloudflared) via winget..."
    winget install --id Cloudflare.cloudflared --accept-package-agreements --accept-source-agreements
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path", "User")
    $cloudflared = Find-Cloudflared
}
if (-not $cloudflared) {
    Write-Error "cloudflared is not on PATH. Install from https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/ then re-run."
}

Write-Host "Using $cloudflared"

$cert = Join-Path $cfDir "cert.pem"
if (-not (Test-Path $cert)) {
    Write-Host ""
    Write-Host "A browser will open so you can authorize this machine on Cloudflare (zone glyphmc.net)."
    Write-Host "Pick the glyphmc.net account, then come back here."
    Write-Host ""
    & $cloudflared tunnel login
}

if (-not (Test-Path $cert)) {
    Write-Error "cloudflared tunnel login did not write $cert"
}

$existing = & $cloudflared tunnel list --output json 2>$null | ConvertFrom-Json
$tunnel = @($existing) | Where-Object { $_.name -eq $tunnelName } | Select-Object -First 1
if (-not $tunnel) {
    Write-Host "Creating tunnel $tunnelName"
    & $cloudflared tunnel create $tunnelName
    $existing = & $cloudflared tunnel list --output json 2>$null | ConvertFrom-Json
    $tunnel = @($existing) | Where-Object { $_.name -eq $tunnelName } | Select-Object -First 1
}
if (-not $tunnel) {
    Write-Error "Could not create or find tunnel $tunnelName"
}

$tunnelId = $tunnel.id
$cred = Join-Path $cfDir "$tunnelId.json"
if (-not (Test-Path $cred)) {
    Write-Error "Missing credentials file $cred"
}

New-Item -ItemType Directory -Force -Path $cfDir | Out-Null
$configPath = Join-Path $cfDir "config.yml"
@"
tunnel: $tunnelId
credentials-file: $cred

ingress:
  - hostname: $hostname
    service: $origin
  - service: http_status:404
"@ | Set-Content -Path $configPath -Encoding ascii
Write-Host "Wrote $configPath"

Write-Host "Routing DNS $hostname -> tunnel (proxied CNAME). Leave this out of DDNS."
& $cloudflared tunnel route dns --overwrite-dns $tunnelName $hostname

if (Test-Path $webserverConf) {
    $raw = Get-Content $webserverConf -Raw
    if ($raw -match '(?m)^ip:') {
        $raw = [regex]::Replace($raw, '(?m)^ip:.*$', 'ip: "127.0.0.1"')
    } else {
        $raw = $raw.TrimEnd() + "`r`n`r`nip: `"127.0.0.1`"`r`n"
    }
    Set-Content -Path $webserverConf -Value $raw -Encoding utf8
    Write-Host "BlueMap webserver bound to 127.0.0.1:8100 (restart Paper SMP to apply)."
} else {
    Write-Host "BlueMap config not generated yet. After the first SMP start, re-run this script so the webserver binds localhost only."
}

$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).
    IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($isAdmin) {
    $svc = Get-Service cloudflared -ErrorAction SilentlyContinue
    if (-not $svc) {
        & $cloudflared service install
        Write-Host "Installed Windows service cloudflared (starts at boot)."
    }
    Start-Service cloudflared -ErrorAction SilentlyContinue
    Write-Host "cloudflared service is running."
} else {
    Write-Host ""
    Write-Host "Re-run this script as Administrator to install the cloudflared Windows service."
    Write-Host "Until then, start the tunnel with:"
    Write-Host "  cloudflared tunnel run $tunnelName"
}

Write-Host ""
Write-Host "Map URLs once SMP + BlueMap + tunnel are up:"
Write-Host "  https://$hostname"
Write-Host "  https://glyphmc.net/map/"
