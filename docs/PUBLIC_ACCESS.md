# Public Access — Domain, DNS and Port Forwarding

How players outside the LAN connect to the dev network. Status of each step
is tracked at the bottom.

```
player  --DNS-->  play.<domain>  =  <home public IP>
   |
   v  TCP 25565
home router  --port forward-->  this PC (Velocity 0.0.0.0:25565)
                                     |
                                     v  127.0.0.1:25566 (never exposed)
                                  Folia backend
Voice chat: UDP 24454, same path.
```

## 1. Domain (manual, one time)

Buy at [Cloudflare Registrar](https://dash.cloudflare.com/?to=/:account/domains/register)
(at-cost, DNS included) — any registrar works if its DNS supports low TTLs.

## 2. DNS record

One **A record**, e.g. `play.<domain>` → current public IP, TTL 60,
**DNS only** (grey cloud — Cloudflare's orange-cloud proxy does not carry
Minecraft traffic on normal plans; do not enable it).

The record is created and kept current automatically by the dynamic DNS
updater (next section) — no need to create it by hand.

## 3. Dynamic DNS (`scripts/cloudflare-ddns.ps1`)

Home IPs change; this script checks the current public IP and updates the
A record when needed.

One-time setup on the hosting PC:

1. Cloudflare dashboard → My Profile → API Tokens → Create Token →
   template "Edit zone DNS", scoped to the zone.
2. Set user environment variables (PowerShell):

   ```powershell
   [Environment]::SetEnvironmentVariable("CLOUDFLARE_API_TOKEN", "<token>", "User")
   [Environment]::SetEnvironmentVariable("GLYPH_DNS_ZONE", "<domain>", "User")
   [Environment]::SetEnvironmentVariable("GLYPH_DNS_RECORD", "play.<domain>", "User")
   ```

3. Run once to create the record: `scripts\cloudflare-ddns.ps1`
4. Schedule every 5 minutes (elevated PowerShell):

   ```powershell
   $action = New-ScheduledTaskAction -Execute "powershell.exe" `
       -Argument "-WindowStyle Hidden -ExecutionPolicy Bypass -File d:\Glyph\Glyph-Server\scripts\cloudflare-ddns.ps1"
   $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
       -RepetitionInterval (New-TimeSpan -Minutes 5)
   Register-ScheduledTask -TaskName "Glyph DDNS" -Action $action -Trigger $trigger
   ```

## 4. Windows Firewall (done — `scripts/setup-firewall.ps1`)

Inbound allow rules, added 2026-08-09:

- `Glyph Minecraft (Velocity TCP 25565)`
- `Glyph Voice Chat (UDP 24454)`

Backend port 25566 is intentionally **not** opened (localhost bind only,
GDD section 38).

## 5. Router port forwarding (manual)

At `http://192.168.1.1` (router admin) forward to this PC
(`192.168.1.108` — reserve this address via DHCP reservation so it cannot
change):

| External port | Protocol | Internal address     | For        |
|---------------|----------|----------------------|------------|
| 25565         | TCP      | 192.168.1.108:25565  | Minecraft  |
| 24454         | UDP      | 192.168.1.108:24454  | Voice chat |

If the router's WAN address is **not** the public IP shown by
`https://api.ipify.org` (e.g. it is in `100.64.x.x`), the ISP uses CGNAT and
port forwarding will not work — options then are asking the ISP for a public
IP, or a tunnel (playit.gg / Cloudflare Tunnel with Spectrum).

## 6. Verify

1. `Test-NetConnection localhost -Port 25565` on this PC (Velocity running).
2. From outside the LAN (phone hotspot): add `play.<domain>` in Minecraft.
3. https://mcsrvstat.us/server/play.<domain> shows the MOTD when reachable.

## Security posture (already in place)

- Velocity: `online-mode = true` (Mojang auth), login rate limit 3s,
  command + tab-complete rate limits, query protocol disabled.
- Folia backend: binds `127.0.0.1:25566`, refuses connections without the
  Velocity forwarding secret. Never port-forward 25566.
- The dev PostgreSQL/Redis bind `127.0.0.1` only (docker-compose).

## Status

- [x] Firewall rules (2026-08-09)
- [x] DDNS updater script ready
- [ ] Domain purchased — pending
- [ ] DDNS environment variables + scheduled task
- [ ] Router port forwarding
- [ ] Outside-LAN connection verified
