# Cloudflare dynamic DNS updater for the Glyph server.
#
# Home connections get a new public IP occasionally; this keeps the play
# domain pointed at the current one. Safe to run repeatedly (it only writes
# when the IP actually changed). Schedule it every 5 minutes (see
# docs/PUBLIC_ACCESS.md).
#
# Requires three environment variables (user-level is fine):
#   CLOUDFLARE_API_TOKEN  API token with Zone:DNS:Edit on the zone
#   GLYPH_DNS_ZONE        the registered domain, e.g. glyphmc.net
#   GLYPH_DNS_RECORD      record name(s), comma separated, e.g.
#                         "play.glyphmc.net,anarchy.glyphmc.net,smp.glyphmc.net"
#                         Do not include the apex (glyphmc.net) — that is the site.

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$token = $env:CLOUDFLARE_API_TOKEN
$zone = $env:GLYPH_DNS_ZONE
$record = $env:GLYPH_DNS_RECORD
if (-not $token -or -not $zone -or -not $record) {
    Write-Error "Set CLOUDFLARE_API_TOKEN, GLYPH_DNS_ZONE and GLYPH_DNS_RECORD first."
    exit 1
}

# While CGNAT forces a playit.gg tunnel, play.glyphmc.net is a CNAME/SRV to
# the tunnel hostname (docs/PUBLIC_ACCESS.md). Do not overwrite those records
# with the desktop's egress IP.
if ($env:GLYPH_USE_PLAYIT -eq "1") {
    Write-Output "$(Get-Date -Format s) GLYPH_USE_PLAYIT=1 - skipping DDNS (tunnel mode)."
    exit 0
}

$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }
$api = "https://api.cloudflare.com/client/v4"

$publicIp = (Invoke-RestMethod "https://api.ipify.org?format=json").ip

$zoneId = (Invoke-RestMethod "$api/zones?name=$zone" -Headers $headers).result[0].id
if (-not $zoneId) {
    Write-Error "Zone $zone not found (check the token's permissions)."
    exit 1
}

foreach ($name in ($record -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
    $existing = (Invoke-RestMethod "$api/zones/$zoneId/dns_records?type=A&name=$name" -Headers $headers).result

    if (-not $existing) {
        # First run: create the record. proxied=false is required - Cloudflare's
        # proxy does not carry Minecraft traffic on normal plans.
        $body = @{ type = "A"; name = $name; content = $publicIp; ttl = 60; proxied = $false } | ConvertTo-Json
        Invoke-RestMethod "$api/zones/$zoneId/dns_records" -Method Post -Headers $headers -Body $body | Out-Null
        Write-Output "$(Get-Date -Format s) created $name -> $publicIp"
    } elseif ($existing[0].content -ne $publicIp) {
        $body = @{ type = "A"; name = $name; content = $publicIp; ttl = 60; proxied = $false } | ConvertTo-Json
        Invoke-RestMethod "$api/zones/$zoneId/dns_records/$($existing[0].id)" -Method Put -Headers $headers -Body $body | Out-Null
        Write-Output "$(Get-Date -Format s) updated $name : $($existing[0].content) -> $publicIp"
    } else {
        Write-Output "$(Get-Date -Format s) unchanged $name ($publicIp)"
    }
}
