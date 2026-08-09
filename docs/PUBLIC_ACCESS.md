# Public Access — glyphmc.net

How players connect to the network. The **desktop PC** (9900X3D, 96 GB,
2.5 Gbps symmetric fiber) is the host. The laptop is a dev machine only —
it sits on Starlink, which is CGNAT and cannot accept inbound connections;
do not try to host from it.

Domain: **glyphmc.net**, registered at Cloudflare (nameservers
kyrie/daisy.ns.cloudflare.com). Players connect to `play.glyphmc.net`
(bare `glyphmc.net` also works).

```
player  --DNS-->  play.glyphmc.net  =  <desktop public IP, kept fresh by DDNS>
   |
   v  TCP 25565
fiber router  --port forward-->  desktop (Velocity 0.0.0.0:25565)
                                     |
                                     v  127.0.0.1:25566 (never exposed)
                                  Folia backend
Voice chat: UDP 24454, same path.
```

## Desktop setup checklist (run ON THE DESKTOP)

### 1. Repo + servers

Clone the repo — the server runtimes, jars and plugins are committed.
Follow the "New machine quick start" in `docs/LOCAL_TEST_SERVER.md`
(Docker stack, run each server once, `scripts\sync-forwarding-secret.ps1`).

### 2. Cloudflare API token (one time)

Cloudflare dashboard → My Profile → API Tokens → Create Token →
template **"Edit zone DNS"** → scope: zone `glyphmc.net`.

### 3. DNS records via the DDNS updater

```powershell
[Environment]::SetEnvironmentVariable("CLOUDFLARE_API_TOKEN", "<token>", "User")
[Environment]::SetEnvironmentVariable("GLYPH_DNS_ZONE", "glyphmc.net", "User")
[Environment]::SetEnvironmentVariable("GLYPH_DNS_RECORD", "play.glyphmc.net,glyphmc.net", "User")
# restart the terminal so the variables load, then:
scripts\cloudflare-ddns.ps1
```

First run creates both A records pointing at the desktop's current public
IP (TTL 60, DNS-only/grey cloud — never enable Cloudflare's orange-cloud
proxy on these; it does not carry Minecraft traffic).

Then schedule it every 5 minutes so IP changes heal automatically
(elevated PowerShell, adjust the repo path):

```powershell
$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-WindowStyle Hidden -ExecutionPolicy Bypass -File <repo>\scripts\cloudflare-ddns.ps1"
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) `
    -RepetitionInterval (New-TimeSpan -Minutes 5)
Register-ScheduledTask -TaskName "Glyph DDNS" -Action $action -Trigger $trigger
```

### 4. Windows Firewall

Run `scripts\setup-firewall.ps1` **as Administrator** on the desktop.
Opens TCP 25565 (Velocity) and UDP 24454 (voice). Backend port 25566 stays
closed on purpose (localhost bind only, GDD section 38).

### 5. Router port forwarding

In the fiber router's admin page, forward to the desktop's LAN address
(set a DHCP reservation for it first so it cannot change):

| External port | Protocol | Internal              | For        |
|---------------|----------|-----------------------|------------|
| 25565         | TCP      | <desktop LAN IP>:25565 | Minecraft  |
| 24454         | UDP      | <desktop LAN IP>:24454 | Voice chat |

Sanity check: the router's WAN address should match
`https://api.ipify.org`. If it does not (or is `100.64.x.x`), the fiber
ISP uses CGNAT too — then we tunnel instead (playit.gg or a $5 VPS).

### 6. Verify

1. On the desktop: `Test-NetConnection localhost -Port 25565`
   (Velocity must be running).
2. From outside the LAN (phone hotspot): connect to `play.glyphmc.net`.
3. https://mcsrvstat.us/server/play.glyphmc.net shows the MOTD when live.

## Security posture (already in place)

- Velocity: `online-mode = true` (Mojang auth), login rate limit 3s,
  command + tab-complete rate limits, query protocol disabled.
- Folia backend: binds `127.0.0.1:25566`, refuses connections without the
  Velocity forwarding secret. Never port-forward 25566.
- Dev PostgreSQL/Redis bind `127.0.0.1` only (docker-compose).
- Keep the Windows account password-protected; consider auto-start via
  Task Scheduler at boot for the Docker stack + both servers later.

## Status

- [x] Domain purchased: glyphmc.net (Cloudflare, 2026-08-09)
- [x] DDNS updater script (multi-record: play + bare domain)
- [x] Firewall script ready (`scripts/setup-firewall.ps1`)
- [ ] Desktop: repo cloned + servers running
- [ ] Desktop: API token + DDNS env vars + first run + scheduled task
- [ ] Desktop: firewall script run as admin
- [ ] Router: DHCP reservation + port forwards (TCP 25565, UDP 24454)
- [ ] Outside-LAN connection verified
