# Minimal Minecraft RCON client for the local backends.
# Folia anarchy: port 25575. Paper SMP: port 25576. Same password.
#
# Usage:
#   .\rcon.ps1 "list"
#   .\rcon.ps1 -Target smp "list"
#   .\rcon.ps1 "worldborder get"

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Command,
    [ValidateSet("folia", "anarchy", "smp")]
    [string]$Target = "folia",
    [string]$HostName = "127.0.0.1",
    [int]$Port = 0,
    [string]$Password = "glyph-dev-rcon"
)
if ($Port -le 0) {
    $Port = if ($Target -eq "smp") { 25576 } else { 25575 }
}

$ErrorActionPreference = "Stop"

function Write-RconPacket([System.IO.Stream]$stream, [int]$id, [int]$type, [string]$body) {
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $length = 4 + 4 + $bodyBytes.Length + 2
    $packet = New-Object byte[] ($length + 4)
    [BitConverter]::GetBytes([int]$length).CopyTo($packet, 0)
    [BitConverter]::GetBytes([int]$id).CopyTo($packet, 4)
    [BitConverter]::GetBytes([int]$type).CopyTo($packet, 8)
    if ($bodyBytes.Length -gt 0) { $bodyBytes.CopyTo($packet, 12) }
    $stream.Write($packet, 0, $packet.Length)
    $stream.Flush()
}

function Read-RconPacket([System.IO.Stream]$stream) {
    $lenBuf = New-Object byte[] 4
    $read = 0
    while ($read -lt 4) {
        $n = $stream.Read($lenBuf, $read, 4 - $read)
        if ($n -le 0) { throw "RCON connection closed while reading length" }
        $read += $n
    }
    $length = [BitConverter]::ToInt32($lenBuf, 0)
    if ($length -lt 10 -or $length -gt 1MB) { throw "Invalid RCON packet length: $length" }
    $payload = New-Object byte[] $length
    $read = 0
    while ($read -lt $length) {
        $n = $stream.Read($payload, $read, $length - $read)
        if ($n -le 0) { throw "RCON connection closed while reading payload" }
        $read += $n
    }
    $id = [BitConverter]::ToInt32($payload, 0)
    $type = [BitConverter]::ToInt32($payload, 4)
    $bodyLen = [Math]::Max(0, $length - 10)
    $body = if ($bodyLen -gt 0) {
        [System.Text.Encoding]::UTF8.GetString($payload, 8, $bodyLen)
    } else { "" }
    [pscustomobject]@{ Id = $id; Type = $type; Body = $body }
}

$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.ReceiveTimeout = 15000
    $client.SendTimeout = 10000
    $client.Connect($HostName, $Port)
    $stream = $client.GetStream()

    Write-RconPacket $stream 1 3 $Password
    $auth = Read-RconPacket $stream
    if ($auth.Id -eq -1) { throw "RCON authentication failed" }

    # Dual-packet trick: Minecraft may split the real response across
    # packets. A follow-up request with a different id lets us know when
    # the original response is finished.
    $reqId = 2
    $endId = 3
    Write-RconPacket $stream $reqId 2 $Command
    Write-RconPacket $stream $endId 2 ""

    $parts = New-Object System.Collections.Generic.List[string]
    while ($true) {
        $packet = Read-RconPacket $stream
        if ($packet.Id -eq $endId) { break }
        if ($packet.Id -eq $reqId -and $packet.Body) {
            $parts.Add($packet.Body)
        }
    }
    $text = ($parts -join "").TrimEnd()
    # Strip common Minecraft formatting / section-sign color codes for readability.
    $text = [regex]::Replace($text, "§.", "")
    if ($text) { Write-Output $text }
} finally {
    $client.Close()
}
