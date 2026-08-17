# Localhost-only Glyph ops page. Bind 127.0.0.1:8787. Never expose this.
# start.bat / scripts\dashboard.ps1 launch this and open the browser.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$webRoot = Join-Path $PSScriptRoot "dashboard"
$rcon = Join-Path $PSScriptRoot "rcon.ps1"
$startAll = Join-Path $PSScriptRoot "start-all.ps1"
$listenUrl = "http://127.0.0.1:8787/"

$logPaths = @{
    anarchy  = Join-Path $root "glyph-folia\logs\latest.log"
    smp      = Join-Path $root "glyph-smp\logs\latest.log"
    velocity = Join-Path $root "glyph-velocity\logs\latest.log"
    discord  = Join-Path $root "glyph-discord\logs\start.log"
}

$playerCache = @{
    at       = [datetime]::MinValue
    anarchy  = @()
    smp      = @()
}
$tickCache = @{
    at      = [datetime]::MinValue
    anarchy = $null
    smp     = $null
}
$discordCache = @{ at = [datetime]::MinValue; up = $false }

function Test-LocalPort([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(150)
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
    if ((Get-Date) - $discordCache.at -lt [TimeSpan]::FromSeconds(6)) {
        return $discordCache.up
    }
    $up = [bool](Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match "glyph-discord-\S+\.jar|GlyphDiscordMain") })
    $discordCache.at = Get-Date
    $discordCache.up = $up
    return $up
}

function Write-McVarInt([int]$value) {
    $bytes = New-Object System.Collections.Generic.List[byte]
    $n = $value
    while ($true) {
        $b = $n -band 127
        $n = $n -shr 7
        if ($n -ne 0) {
            $bytes.Add([byte]($b -bor 128))
        } else {
            $bytes.Add([byte]$b)
            break
        }
    }
    return ,$bytes.ToArray()
}

function Read-McVarInt([System.IO.Stream]$stream) {
    $n = 0
    $shift = 0
    while ($true) {
        $b = $stream.ReadByte()
        if ($b -lt 0) { throw "status ping closed" }
        $n = $n -bor (($b -band 127) -shl $shift)
        if (($b -band 128) -eq 0) { return $n }
        $shift += 7
        if ($shift -gt 35) { throw "bad varint" }
    }
}

# Server-list ping (no RCON) so player polls do not spam latest.log.
function Get-OnlineNames([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne(400)) { return ,@() }
        $client.EndConnect($iar)
        $stream = $client.GetStream()
        $stream.ReadTimeout = 800
        $stream.WriteTimeout = 800
        $hostBytes = [Text.Encoding]::UTF8.GetBytes("127.0.0.1")
        $handshake = New-Object System.Collections.Generic.List[byte]
        $handshake.AddRange((Write-McVarInt 0))
        $handshake.AddRange((Write-McVarInt 770))
        $handshake.AddRange((Write-McVarInt $hostBytes.Length))
        $handshake.AddRange($hostBytes)
        $handshake.Add([byte](($port -shr 8) -band 255))
        $handshake.Add([byte]($port -band 255))
        $handshake.AddRange((Write-McVarInt 1))
        $packet = New-Object System.Collections.Generic.List[byte]
        $packet.AddRange((Write-McVarInt $handshake.Count))
        $packet.AddRange($handshake.ToArray())
        $packet.AddRange((Write-McVarInt 1))
        $packet.AddRange((Write-McVarInt 0))
        $stream.Write($packet.ToArray(), 0, $packet.Count)
        [void](Read-McVarInt $stream)
        [void](Read-McVarInt $stream)
        $jsonLen = Read-McVarInt $stream
        if ($jsonLen -le 0 -or $jsonLen -gt 200000) { return ,@() }
        $buf = New-Object byte[] $jsonLen
        $read = 0
        while ($read -lt $jsonLen) {
            $n = $stream.Read($buf, $read, $jsonLen - $read)
            if ($n -le 0) { break }
            $read += $n
        }
        $json = [Text.Encoding]::UTF8.GetString($buf, 0, $read) | ConvertFrom-Json
        $sample = $json.players.sample
        if (-not $sample) { return ,@() }
        return ,@(@($sample) | ForEach-Object { $_.name } | Where-Object { $_ })
    } catch {
        return ,@()
    } finally {
        $client.Close()
    }
}

function Refresh-Players {
    if ((Get-Date) - $playerCache.at -lt [TimeSpan]::FromSeconds(8)) { return }
    $playerCache.anarchy = @(if (Test-LocalPort 25566) { Get-OnlineNames 25566 })
    $playerCache.smp = @(if (Test-LocalPort 25567) { Get-OnlineNames 25567 })
    $playerCache.at = Get-Date
}

function Strip-McText([string]$text) {
    if (-not $text) { return "" }
    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $text.Length; $i++) {
        $ch = [int][char]$text[$i]
        if ($ch -eq 0x15 -or $ch -eq 0xA7) {
            $i++
            continue
        }
        if ($ch -lt 32 -and $ch -ne 10 -and $ch -ne 13 -and $ch -ne 9) {
            continue
        }
        [void]$sb.Append($text[$i])
    }
    return $sb.ToString()
}

function New-TickStats {
    return [pscustomobject]@{
        tps     = $null
        tps5    = $null
        tps15   = $null
        mspt    = $null
        msptMax = $null
        regions = $null
        util    = $null
    }
}

function Read-PaperTicks([string]$raw) {
    $stats = New-TickStats
    $text = Strip-McText $raw
    $tps = [regex]::Match($text, "TPS from last 1m, 5m, 15m:\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)")
    if ($tps.Success) {
        $stats.tps = [double]$tps.Groups[1].Value
        $stats.tps5 = [double]$tps.Groups[2].Value
        $stats.tps15 = [double]$tps.Groups[3].Value
    }
    $ms = [regex]::Matches($text, "([\d.]+)/([\d.]+)/([\d.]+)")
    if ($ms.Count -ge 3) {
        $oneMin = $ms[2]
        $stats.mspt = [double]$oneMin.Groups[1].Value
        $stats.msptMax = [double]$oneMin.Groups[3].Value
    } elseif ($ms.Count -ge 1) {
        $stats.mspt = [double]$ms[0].Groups[1].Value
        $stats.msptMax = [double]$ms[0].Groups[3].Value
    }
    return $stats
}

function Read-FoliaTicks([string]$raw) {
    $stats = New-TickStats
    $text = Strip-McText $raw
    $med = [regex]::Match($text, "Median Region TPS:\s*([\d.]+)")
    $low = [regex]::Match($text, "Lowest Region TPS:\s*([\d.]+)")
    $high = [regex]::Match($text, "Highest Region TPS:\s*([\d.]+)")
    $regions = [regex]::Match($text, "Total regions:\s*(\d+)")
    $util = [regex]::Match($text, "Utilisation:\s*([\d.]+)\s*%")
    if ($med.Success) { $stats.tps = [double]$med.Groups[1].Value }
    if ($low.Success) { $stats.tps5 = [double]$low.Groups[1].Value }
    if ($high.Success) { $stats.tps15 = [double]$high.Groups[1].Value }
    if ($regions.Success) { $stats.regions = [int]$regions.Groups[1].Value }
    if ($util.Success) { $stats.util = [double]$util.Groups[1].Value }
    $ms = [regex]::Matches($text, "([\d.]+)/([\d.]+)/([\d.]+)")
    if ($ms.Count -ge 3) {
        $oneMin = $ms[2]
        $stats.mspt = [double]$oneMin.Groups[1].Value
        $stats.msptMax = [double]$oneMin.Groups[3].Value
    }
    return $stats
}

function Refresh-Ticks {
    if ((Get-Date) - $tickCache.at -lt [TimeSpan]::FromSeconds(8)) { return }
    if (Test-LocalPort 25566) {
        try {
            $tps = Invoke-Rcon "anarchy" "tps"
            $mspt = Invoke-Rcon "anarchy" "mspt"
            $tickCache.anarchy = Read-FoliaTicks ($tps + "`n" + $mspt)
        } catch {
            # keep last sample
        }
    } else {
        $tickCache.anarchy = $null
    }
    if (Test-LocalPort 25567) {
        try {
            $tps = Invoke-Rcon "smp" "tps"
            $mspt = Invoke-Rcon "smp" "mspt"
            $tickCache.smp = Read-PaperTicks ($tps + "`n" + $mspt)
        } catch {
            # keep last sample
        }
    } else {
        $tickCache.smp = $null
    }
    $tickCache.at = Get-Date
}

function Get-StatusObject {
    Refresh-Players
    Refresh-Ticks
    return [pscustomobject]@{
        anarchy  = [pscustomobject]@{
            up      = [bool](Test-LocalPort 25566)
            players = [string[]]@($playerCache.anarchy | Where-Object { $_ })
            ticks   = $tickCache.anarchy
        }
        smp      = [pscustomobject]@{
            up      = [bool](Test-LocalPort 25567)
            players = [string[]]@($playerCache.smp | Where-Object { $_ })
            ticks   = $tickCache.smp
        }
        velocity = [pscustomobject]@{ up = [bool](Test-LocalPort 25565) }
        discord  = [pscustomobject]@{ up = [bool](Test-DiscordRunning) }
        postgres = [pscustomobject]@{ up = [bool](Test-LocalPort 5432) }
        redis    = [pscustomobject]@{ up = [bool](Test-LocalPort 6379) }
    }
}

function Read-LogChunk([string]$source, [int64]$offset) {
    if (-not $logPaths.ContainsKey($source)) {
        throw "Unknown log source"
    }
    $path = $logPaths[$source]
    if (-not (Test-Path $path)) {
        return [pscustomobject]@{ offset = 0; text = "" }
    }
    $fs = $null
    try {
        $fs = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        if ($offset -lt 0) {
            $offset = [Math]::Max(0, $fs.Length - 80000)
        }
        if ($fs.Length -lt $offset) { $offset = 0 }
        $fs.Seek($offset, [IO.SeekOrigin]::Begin) | Out-Null
        $reader = New-Object IO.StreamReader($fs)
        $text = $reader.ReadToEnd()
        if ($text) {
            $kept = New-Object System.Collections.Generic.List[string]
            foreach ($line in ($text -split "`r?`n")) {
                if ($line -match "Thread RCON |RCON Listener|RCON Client") { continue }
                $kept.Add($line)
            }
            $text = ($kept -join "`n")
        }
        return [pscustomobject]@{ offset = [int64]$fs.Position; text = $text }
    } finally {
        if ($fs) { $fs.Dispose() }
    }
}

function Read-JsonBody($req) {
    $reader = New-Object IO.StreamReader($req.InputStream, [Text.Encoding]::UTF8)
    try {
        $raw = $reader.ReadToEnd()
        if ([string]::IsNullOrWhiteSpace($raw)) { return $null }
        return $raw | ConvertFrom-Json
    } finally {
        $reader.Close()
    }
}

function Send-Bytes($res, [int]$status, [string]$contentType, [byte[]]$bytes) {
    $res.StatusCode = $status
    $res.ContentType = $contentType
    $res.ContentEncoding = [Text.Encoding]::UTF8
    $res.Headers.Add("Cache-Control", "no-store")
    $res.ContentLength64 = $bytes.Length
    if ($bytes.Length -gt 0) {
        $res.OutputStream.Write($bytes, 0, $bytes.Length)
    }
}

function Send-Text($res, [int]$status, [string]$contentType, [string]$text) {
    Send-Bytes $res $status $contentType ([Text.Encoding]::UTF8.GetBytes($text))
}

function Send-Json($res, [int]$status, $obj) {
    Send-Text $res $status "application/json; charset=utf-8" ($obj | ConvertTo-Json -Depth 8 -Compress)
}

function Test-LocalHostHeader([string]$header) {
    if ([string]::IsNullOrWhiteSpace($header)) { return $false }
    $name = $header.Split(":")[0].ToLowerInvariant()
    return ($name -eq "127.0.0.1" -or $name -eq "localhost")
}

function Start-StackHidden {
    Start-Process powershell -WindowStyle Hidden -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $startAll
    ) -WorkingDirectory $root
}

function Start-RconAsync([string]$target, [string]$command) {
    $args = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $rcon)
    if ($target -eq "smp") { $args += @("-Target", "smp") }
    $args += $command
    Start-Process powershell -WindowStyle Hidden -ArgumentList $args -WorkingDirectory $root
}

function Invoke-Rcon([string]$target, [string]$command) {
    if ($target -eq "smp") {
        return ((& $rcon -Target smp $command) | Out-String).Trim()
    }
    return ((& $rcon $command) | Out-String).Trim()
}

function Resolve-StaticPath([string]$urlPath) {
    $rel = $urlPath.TrimStart("/")
    if ([string]::IsNullOrWhiteSpace($rel) -or $rel -eq "/") { $rel = "index.html" }
    if ($rel -eq "logo.png") {
        return @{ path = (Join-Path $root "assets\glyph-logo.png"); type = "image/png" }
    }
    $allowed = @{
        "index.html" = "text/html; charset=utf-8"
        "styles.css" = "text/css; charset=utf-8"
        "app.js"     = "application/javascript; charset=utf-8"
    }
    if (-not $allowed.ContainsKey($rel)) { return $null }
    $full = Join-Path $webRoot $rel
    $resolved = [IO.Path]::GetFullPath($full)
    if (-not $resolved.StartsWith([IO.Path]::GetFullPath($webRoot))) { return $null }
    return @{ path = $resolved; type = $allowed[$rel] }
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($listenUrl)
try {
    $listener.Start()
} catch {
    Write-Error "Could not bind $listenUrl - is the dashboard already running?"
    exit 1
}
Write-Output "Glyph ops dashboard at $listenUrl"

try {
    while ($listener.IsListening) {
        $ctx = $listener.GetContext()
        $req = $ctx.Request
        $res = $ctx.Response
        try {
            if (-not (Test-LocalHostHeader $req.Headers["Host"])) {
                Send-Json $res 403 ([pscustomobject]@{ error = "localhost only" })
                continue
            }
            $path = $req.Url.AbsolutePath
            $method = $req.HttpMethod

            if ($method -eq "GET" -and $path -eq "/api/status") {
                Send-Json $res 200 (Get-StatusObject)
                continue
            }
            if ($method -eq "GET" -and $path -eq "/api/logs") {
                $source = $req.QueryString["source"]
                if ([string]::IsNullOrWhiteSpace($source)) { $source = "anarchy" }
                $offset = 0
                [int64]::TryParse($req.QueryString["offset"], [ref]$offset) | Out-Null
                Send-Json $res 200 (Read-LogChunk $source $offset)
                continue
            }
            if ($method -eq "POST" -and $path -eq "/api/rcon") {
                $body = Read-JsonBody $req
                $command = [string]$body.command
                $target = [string]$body.target
                if ([string]::IsNullOrWhiteSpace($command) -or $command.Length -gt 200) {
                    Send-Json $res 400 ([pscustomobject]@{ error = "bad command" })
                    continue
                }
                if ($target -ne "smp") { $target = "anarchy" }
                $out = Invoke-Rcon $target $command
                Send-Json $res 200 ([pscustomobject]@{ output = $out })
                continue
            }
            if ($method -eq "POST" -and $path -eq "/api/action") {
                $body = Read-JsonBody $req
                $action = [string]$body.action
                switch ($action) {
                    "start" { Start-StackHidden }
                    "restart-anarchy" { Start-RconAsync "anarchy" "restart" }
                    "restart-smp" { Start-RconAsync "smp" "restart" }
                    default {
                        Send-Json $res 400 ([pscustomobject]@{ error = "unknown action" })
                        continue
                    }
                }
                Send-Json $res 200 ([pscustomobject]@{ ok = $true })
                continue
            }
            if ($method -eq "GET") {
                $file = Resolve-StaticPath $path
                if ($file -and (Test-Path $file.path)) {
                    Send-Bytes $res 200 $file.type ([IO.File]::ReadAllBytes($file.path))
                    continue
                }
                Send-Json $res 404 ([pscustomobject]@{ error = "not found" })
                continue
            }
            Send-Json $res 405 ([pscustomobject]@{ error = "method not allowed" })
        } catch {
            try {
                Send-Json $res 500 ([pscustomobject]@{ error = $_.Exception.Message })
            } catch { }
        } finally {
            try { $res.OutputStream.Close() } catch { }
            try { $res.Close() } catch { }
        }
    }
} finally {
    if ($listener.IsListening) { $listener.Stop() }
    $listener.Close()
}
