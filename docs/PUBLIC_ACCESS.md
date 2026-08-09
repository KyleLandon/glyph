# Public Access — glyphmc.net

How players connect to the network. The **desktop PC** (9900X3D, 96 GB,
2.5 Gbps symmetric fiber) is the host. The laptop is a dev machine only —
it sits on Starlink, which is CGNAT and cannot accept inbound connections;
do not try to host from it.

Domain: **glyphmc.net**, registered at Cloudflare (nameservers
kyrie/daisy.ns.cloudflare.com). Players connect to `play.glyphmc.net`.

## Current path: playit.gg tunnel (temporary)

Fiber ISP puts the desktop behind CGNAT (WAN `100.64.x.x`), so inbound port
forwards cannot work until they issue a public/static IP. Until then we
tunnel through [playit.gg](https://playit.gg):

```
player  -->  play.glyphmc.net
               SRV _minecraft._tcp.play  -->  atoms-simmering.tun.ply.gg:60307
               |
               v  playit relay
playitd agent on desktop  -->  127.0.0.1:25565 (Velocity)
                                    |
                                    v  127.0.0.1:25566
                                 Folia backend
Voice: UDP tunnel --> 127.0.0.1:24454  (voice_host set to playit UDP endpoint)
```

| What | Address |
|------|---------|
| Player connect | **`play.glyphmc.net`** (SRV → tunnel; also works as CNAME) |
| Tunnel hostname | `atoms-simmering.tun.ply.gg` (port 60307 if needed) |
| Voice (UDP) | `atoms-composers.tun.ply.gg:60308` → local `24454` |
| Agent | Windows service `playitd` (`winget install DevelopedMethods.playit`) |
| Proxy Protocol | **off** — keeps local `localhost:25565` joins working |

Do **not** give players the raw IP — playit's free tunnels reject bare-IP
Minecraft handshakes (`hostname_verify_level = NoRawIp`).

`GLYPH_USE_PLAYIT=1` (User env) makes `scripts\cloudflare-ddns.ps1` a no-op so
it does not stomp the CNAME/SRV with the CGNAT egress IP.

### When the ISP gives a public IP

1. Set router port forwards (TCP 25565, UDP 24454) to the desktop.
2. Clear `voice_host` in `glyph-folia/plugins/voicechat/voicechat-server.properties`
   (and `bind_address` back to blank / `*`).
3. Delete the Cloudflare CNAME + SRV for `play`; restore A records via DDNS.
4. Set `GLYPH_USE_PLAYIT=0` (or remove the variable) and run
   `scripts\cloudflare-ddns.ps1`.
5. Stop playit: `playit stop` (optional uninstall).
6. Outside-LAN test to `play.glyphmc.net` on the normal port.

## Desktop setup checklist

### 1. Repo + servers

Clone the repo — the server runtimes, jars and plugins are committed.
Follow the "New machine quick start" in `docs/LOCAL_TEST_SERVER.md`
(Docker stack, run each server once, `scripts\sync-forwarding-secret.ps1`).

### 2. Cloudflare API token (one time)

Cloudflare dashboard → My Profile → API Tokens → Create Token →
template **"Edit zone DNS"** → scope: zone `glyphmc.net`.

### 3. DNS / DDNS

With the tunnel active, DNS is managed as CNAME + SRV (above). After the
public IP arrives, restore DDNS:

```powershell
[Environment]::SetEnvironmentVariable("CLOUDFLARE_API_TOKEN", "<token>", "User")
[Environment]::SetEnvironmentVariable("GLYPH_DNS_ZONE", "glyphmc.net", "User")
[Environment]::SetEnvironmentVariable("GLYPH_DNS_RECORD", "play.glyphmc.net,glyphmc.net", "User")
[Environment]::SetEnvironmentVariable("GLYPH_USE_PLAYIT", "0", "User")
scripts\cloudflare-ddns.ps1
```

Scheduled task "Glyph DDNS" every 5 minutes — safe while `GLYPH_USE_PLAYIT=1`
(it exits immediately).

### 4. Windows Firewall

Run `scripts\setup-firewall.ps1` **as Administrator** on the desktop.
Opens TCP 25565 (Velocity) and UDP 24454 (voice). Backend port 25566 stays
closed on purpose (localhost bind only, GDD section 38).

### 5. Router port forwarding (blocked until public IP)

| External port | Protocol | Internal              | For        |
|---------------|----------|-----------------------|------------|
| 25565         | TCP      | <desktop LAN IP>:25565 | Minecraft  |
| 24454         | UDP      | <desktop LAN IP>:24454 | Voice chat |

Sanity check: the router's WAN address should match
`https://api.ipify.org`. If it does not (or is `100.64.x.x`), stay on the
tunnel.

### 6. Verify

1. On the desktop: `Test-NetConnection localhost -Port 25565`
2. From outside the LAN (phone hotspot): connect to `play.glyphmc.net`
3. https://mcsrvstat.us/server/play.glyphmc.net shows the MOTD when live
4. Or `atoms-simmering.tun.ply.gg:60307` (hostname required — not the raw IP)

## Security posture (already in place)

- Velocity: `online-mode = true` (Mojang auth), login rate limit 3s,
  command + tab-complete rate limits, query protocol disabled.
- Folia backend: binds `127.0.0.1:25566`, refuses connections without the
  Velocity forwarding secret. Never port-forward 25566.
- Dev PostgreSQL/Redis bind `127.0.0.1` only (docker-compose).
- Keep the Windows account password-protected.
- Auto-start: the "Glyph Servers" scheduled task runs `scripts/start-all.ps1`
  at logon — Docker engine, PostgreSQL + Redis, playitd, backend, proxy.
  Server start scripts prefer `JAVA_HOME` (User env, JDK 25 on the desktop).
- playit agent secret lives in `C:\ProgramData\playit_gg\playit.toml` — never
  commit it.

## Status

- [x] Domain purchased: glyphmc.net (Cloudflare, 2026-08-09)
- [x] DDNS updater script (multi-record: play + bare domain)
- [x] Firewall script ready (`scripts/setup-firewall.ps1`)
- [x] Desktop: repo cloned + servers running (2026-08-09)
- [x] Desktop: API token + DDNS env vars + first run + scheduled task (2026-08-09)
- [x] Desktop: firewall script run as admin (2026-08-09)
- [x] **CGNAT workaround: playit.gg tunnel** (2026-08-09)
  - Agent claimed, service `playitd` running
  - TCP tunnel → Velocity `:25565` (`atoms-simmering.tun.ply.gg:60307`)
  - UDP tunnel → voice `:24454` (`147.185.221.230:60308`)
  - `play.glyphmc.net` CNAME + Minecraft SRV → tunnel
  - `GLYPH_USE_PLAYIT=1` pauses A-record DDNS
- [ ] ISP public/static IP (then tear down tunnel — steps above)
- [ ] Router: DHCP reservation + port forwards — desktop LAN IP 192.168.4.24
- [x] Outside path verified via tunnel Server List Ping to
      `atoms-simmering.tun.ply.gg:60307` / SRV `play.glyphmc.net` (2026-08-09)
- [ ] Outside-LAN connection verified on native public IP (post-CGNAT)
