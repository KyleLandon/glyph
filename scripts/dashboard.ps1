# Opens the local Glyph ops page (http://127.0.0.1:8787).
# Starts the HTTP server if needed, then the stack if anything is down.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$web = Join-Path $PSScriptRoot "dashboard-web.ps1"
$startAll = Join-Path $PSScriptRoot "start-all.ps1"
$url = "http://127.0.0.1:8787/"

function Test-LocalPort([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(200)
        if (-not $ok) { return $false }
        $client.EndConnect($iar)
        return $client.Connected
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Test-DiscordRunning {
    [bool](Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match "glyph-discord-\S+\.jar|GlyphDiscordMain") })
}

if (-not (Test-LocalPort 8787)) {
    Start-Process powershell -WindowStyle Hidden -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $web
    ) -WorkingDirectory $root
    $deadline = (Get-Date).AddSeconds(8)
    while ((Get-Date) -lt $deadline) {
        if (Test-LocalPort 8787) { break }
        Start-Sleep -Milliseconds 200
    }
}

Start-Process $url

$smpJar = [bool](Get-ChildItem (Join-Path $root "glyph-smp") -Filter "paper-*.jar" -File -ErrorAction SilentlyContinue)
$smpDown = $smpJar -and -not (Test-LocalPort 25567)
if (-not (Test-LocalPort 25566) -or -not (Test-LocalPort 25565) -or -not (Test-DiscordRunning) -or $smpDown) {
    Start-Process powershell -WindowStyle Hidden -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $startAll
    ) -WorkingDirectory $root
}
