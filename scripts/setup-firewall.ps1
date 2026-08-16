# Opens the Windows Firewall for the public-facing Glyph services.
# Run as Administrator. Idempotent: removes old rules with the same name first.
#
#   TCP 25565 -> Velocity proxy (Minecraft)
#   UDP 24454 -> Simple Voice Chat (anarchy)
#   UDP 24455 -> Simple Voice Chat (Forever World)
#
# BlueMap is localhost + Cloudflare Tunnel (map.glyphmc.net). Do not open 8100.
# The Folia backend (25566) stays closed: it binds 127.0.0.1 only and must
# never be reachable from outside (GDD section 38).

$rules = @(
    @{ Name = "Glyph Minecraft (Velocity TCP 25565)"; Protocol = "TCP"; Port = 25565 },
    @{ Name = "Glyph Voice Chat (UDP 24454)";         Protocol = "UDP"; Port = 24454 },
    @{ Name = "Glyph SMP Voice Chat (UDP 24455)";     Protocol = "UDP"; Port = 24455 }
)

foreach ($rule in $rules) {
    Remove-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $rule.Name `
        -Direction Inbound -Protocol $rule.Protocol -LocalPort $rule.Port `
        -Action Allow -Profile Any | Out-Null
    Write-Host "OK: $($rule.Name)"
}

Write-Host "Done. Backend ports 25566/25567 intentionally NOT opened."
