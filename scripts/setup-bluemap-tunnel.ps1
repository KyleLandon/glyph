# One-time: expose Forever World BlueMap at https://map.glyphmc.net
# via Cloudflare Tunnel. HTTPS, no router port 8100, no DDNS for "map".
#
# First run opens a browser for Cloudflare login. Service install needs
# Administrator (the script re-launches itself with UAC for that step).
#
#   scripts\setup-bluemap-tunnel.ps1
#
# Creates tunnel glyph-map, binds BlueMap to 127.0.0.1:8100, installs the
# cloudflared Windows service so it survives reboot.

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$hostname = "map.glyphmc.net"
$tunnelName = "glyph-map"
$origin = "http://127.0.0.1:8100"
$logFile = Join-Path $env:TEMP "glyph-bluemap-tunnel.log"

function Test-IsAdmin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($id)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Write-Log([string]$message) {
    $line = "$(Get-Date -Format s) $message"
    Add-Content -Path $logFile -Value $line
    Write-Host $message
}

function Get-Cloudflared {
    $cmd = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($candidate in @(
            "${env:ProgramFiles}\cloudflared\cloudflared.exe",
            "${env:ProgramFiles(x86)}\cloudflared\cloudflared.exe",
            (Join-Path $env:LOCALAPPDATA "cloudflared\cloudflared.exe")
        )) {
        if (Test-Path $candidate) { return $candidate }
    }
    return $null
}

Write-Log "Starting setup-bluemap-tunnel.ps1 (admin=$(Test-IsAdmin))"

$cloudflared = Get-Cloudflared
if (-not $cloudflared) {
    Write-Log "Installing cloudflared..."
    $destDir = if (Test-IsAdmin) {
        Join-Path $env:ProgramFiles "cloudflared"
    } else {
        Join-Path $env:LOCALAPPDATA "cloudflared"
    }
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
    $exe = Join-Path $destDir "cloudflared.exe"
    Invoke-WebRequest -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" `
        -OutFile $exe -Headers @{ "User-Agent" = "glyph-setup-bluemap/0.1" }
    $env:PATH = "$destDir;" + $env:PATH
    $cloudflared = Get-Cloudflared
    if (-not $cloudflared) {
        Write-Error "cloudflared download failed. See $logFile"
    }
}

Write-Log "Using $cloudflared"

$userCf = Join-Path $env:USERPROFILE ".cloudflared"
$systemCf = "C:\Windows\System32\config\systemprofile\.cloudflared"
New-Item -ItemType Directory -Force -Path $userCf | Out-Null

$cert = Join-Path $userCf "cert.pem"
if (-not (Test-Path $cert)) {
    Write-Log "A browser will open. Log into Cloudflare and authorize zone glyphmc.net."
    & $cloudflared tunnel login
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $cert)) {
        Write-Error "cloudflared login did not write $cert"
    }
}

$existing = & $cloudflared tunnel list --output json 2>$null | ConvertFrom-Json
$tunnel = @($existing) | Where-Object { $_.name -eq $tunnelName } | Select-Object -First 1
if (-not $tunnel) {
    Write-Log "Creating tunnel $tunnelName..."
    & $cloudflared tunnel create $tunnelName
    if ($LASTEXITCODE -ne 0) { Write-Error "Failed to create tunnel $tunnelName" }
    $existing = & $cloudflared tunnel list --output json 2>$null | ConvertFrom-Json
    $tunnel = @($existing) | Where-Object { $_.name -eq $tunnelName } | Select-Object -First 1
}
if (-not $tunnel) { Write-Error "Tunnel $tunnelName was not found after create." }

$tunnelId = [string]$tunnel.id
Write-Log "Tunnel $tunnelName id=$tunnelId"

$credName = "$tunnelId.json"
$userCred = Join-Path $userCf $credName
if (-not (Test-Path $userCred)) {
    Write-Error "Missing credentials file $userCred"
}

$userConfigBody = @"
tunnel: $tunnelId
credentials-file: $userCred

ingress:
  - hostname: $hostname
    service: $origin
  - service: http_status:404
"@
$userConfig = Join-Path $userCf "config.yml"
Set-Content -Path $userConfig -Value $userConfigBody -Encoding UTF8
Write-Log "Wrote $userConfig"

Write-Log "Routing DNS $hostname -> $tunnelName (do not add this name to DDNS)..."
& $cloudflared tunnel route dns $tunnelName $hostname
if ($LASTEXITCODE -ne 0) {
    Write-Log "DNS route command returned $LASTEXITCODE (record may already exist). Continuing."
}

$webserver = Join-Path $root "glyph-smp\plugins\BlueMap\webserver.conf"
if (Test-Path $webserver) {
    $conf = Get-Content $webserver -Raw
    if ($conf -notmatch '(?m)^ip:\s*"127\.0\.0\.1"') {
        $conf = $conf -replace '(?m)^port:\s*8100\s*$', "ip: `"127.0.0.1`"`r`nport: 8100"
        Set-Content -Path $webserver -Value $conf -Encoding UTF8 -NoNewline
        Write-Log "Bound BlueMap to 127.0.0.1:8100"
    } else {
        Write-Log "BlueMap already bound to 127.0.0.1:8100"
    }
}

$rcon = Join-Path $PSScriptRoot "rcon.ps1"
if (Test-Path $rcon) {
    try {
        & $rcon -Target smp "bluemap reload"
        Write-Log "Reloaded BlueMap"
    } catch {
        Write-Log "Could not reload BlueMap via RCON. Restart Forever World later."
    }
}

if (-not (Test-IsAdmin)) {
    Write-Log "Requesting Administrator to install the cloudflared service..."
    $script = Join-Path $PSScriptRoot "setup-bluemap-tunnel.ps1"
    Start-Process powershell -Verb RunAs -Wait -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $script
    )
    $svc = Get-Service -Name "Cloudflared" -ErrorAction SilentlyContinue
    if ($svc) {
        Write-Log "Cloudflared service: $($svc.Status)"
        Write-Log "Done. Map URL: https://$hostname"
        Write-Log "Site viewer: https://glyphmc.net/map/"
        return
    }
    Write-Log "Service not installed. Starting a user-session tunnel until you re-run as Admin."
    Start-Process -FilePath $cloudflared -ArgumentList @("tunnel", "--config", $userConfig, "run") -WindowStyle Hidden
    Write-Log "User tunnel started. Map URL: https://$hostname"
    return
}

New-Item -ItemType Directory -Force -Path $systemCf | Out-Null
Copy-Item $userCred -Destination (Join-Path $systemCf $credName) -Force
Copy-Item $cert -Destination (Join-Path $systemCf "cert.pem") -Force

$systemConfigBody = @"
tunnel: $tunnelId
credentials-file: $systemCf\$credName

ingress:
  - hostname: $hostname
    service: $origin
  - service: http_status:404
"@
$systemConfig = Join-Path $systemCf "config.yml"
Set-Content -Path $systemConfig -Value $systemConfigBody -Encoding UTF8
Write-Log "Wrote $systemConfig"

$svc = Get-Service -Name "Cloudflared" -ErrorAction SilentlyContinue
if (-not $svc) {
    Write-Log "Installing cloudflared Windows service..."
    & $cloudflared service install
    if ($LASTEXITCODE -ne 0) { Write-Error "cloudflared service install failed" }
} else {
    Write-Log "Cloudflared service already installed"
}

$bin = "$cloudflared --config $systemConfig tunnel run"
sc.exe config Cloudflared binPath= $bin | Out-Null
Write-Log "Set Cloudflared binPath to tunnel run with system config"

Restart-Service -Name "Cloudflared" -Force -ErrorAction SilentlyContinue
Start-Service -Name "Cloudflared" -ErrorAction SilentlyContinue
$svc = Get-Service -Name "Cloudflared" -ErrorAction SilentlyContinue
if ($svc) {
    Write-Log "Cloudflared service: $($svc.Status)"
} else {
    Write-Error "Cloudflared service is not present after install."
}

Write-Log "Done. Map URL: https://$hostname"
Write-Log "Site viewer: https://glyphmc.net/map/"
Write-Log "Do not add 'map' to GLYPH_DNS_RECORD. The tunnel owns that hostname."
