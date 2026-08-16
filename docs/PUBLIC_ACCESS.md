# Public Access — glyphmc.net

How players connect to the network. The **desktop PC** (9900X3D, 96 GB,
2.5 Gbps symmetric fiber) is the host. The laptop is a dev machine only —
it sits on Starlink, which is CGNAT and cannot accept inbound connections;
do not try to host from it.

Domain: **glyphmc.net**, registered at Cloudflare (nameservers
kyrie/daisy.ns.cloudflare.com). Players connect to `anarchy.glyphmc.net` or
`smp.glyphmc.net` (`play.glyphmc.net` still lands on anarchy). Marketing
site: **https://glyphmc.net** (Cloudflare Pages project `glyph`).

## Current path: public IP (no tunnel)

The ISP handed over a public WAN address. playit.gg is **stopped**.
`GLYPH_USE_PLAYIT=0`. Cloudflare A records (unproxied, 60s TTL) are kept
in sync by `scripts\cloudflare-ddns.ps1`.

```
player  -->  anarchy.glyphmc.net / smp.glyphmc.net / play.glyphmc.net
               A  -->  desktop public IP :25565
               |
               v
Velocity 0.0.0.0:25565  (forced-hosts pick the backend)
               |
               v  127.0.0.1:25566          v  127.0.0.1:25567
            Folia anarchy                 Paper SMP
Voice: UDP 24454 (anarchy) / 24455 (SMP) on the desktop NIC
```

| What | Address |
|------|---------|
| Anarchy | **`anarchy.glyphmc.net`** |
| SMP | **`smp.glyphmc.net`** |
| Default / alias | **`play.glyphmc.net`** (anarchy) |
| Voice | UDP 24454 anarchy, UDP 24455 SMP |
| Forever World map | **https://map.glyphmc.net** (Cloudflare Tunnel → BlueMap `127.0.0.1:8100`) |

No Minecraft SRV records. Default port 25565. Forced hosts see the name
the player typed, so `smp.glyphmc.net` lands on Paper.

Do **not** give players the raw IP — use the hostnames so Velocity can
route, and so DDNS can move the address later.

### Router port forwards (required)

Desktop LAN IP is **192.168.4.24** (reserve it in DHCP).

| External port | Protocol | Internal                 | For        |
|---------------|----------|--------------------------|------------|
| 25565         | TCP      | 192.168.4.24:25565       | Minecraft  |
| 24454         | UDP      | 192.168.4.24:24454       | Anarchy voice |
| 24455         | UDP      | 192.168.4.24:24455       | SMP voice  |

Never forward 25566, 25567, or BlueMap 8100. The map is HTTPS via Cloudflare
Tunnel, not a router port.

### Forever World map (BlueMap)

Live tiles cannot live on Cloudflare Pages. The site at
**https://glyphmc.net/map/** is a full-page iframe of
**https://map.glyphmc.net**, which a Cloudflare Tunnel on the desktop
forwards to BlueMap at `127.0.0.1:8100`.

One-time on the desktop (Admin to install the Windows service):

```powershell
scripts\setup-bluemap-tunnel.ps1
```

That logs into Cloudflare in a browser, creates tunnel `glyph-map`, points
`map.glyphmc.net` at it (proxied CNAME), binds BlueMap to localhost, and
installs `cloudflared` so the tunnel survives reboot. `start-all.ps1` will
start the service if it is down.

Do not orange-cloud Minecraft hostnames. Do not add `map.glyphmc.net` to
`GLYPH_DNS_RECORD`.

Sanity check: the router's WAN address should match
`https://api.ipify.org`. If it is `100.64.x.x` again, set
`GLYPH_USE_PLAYIT=1` and bring playit back.

### If you must go back to a tunnel

1. Set `GLYPH_USE_PLAYIT=1` (User env).
2. Start playit: `playit start`.
3. Restore CNAME + `_minecraft._tcp` SRV to the tunnel host (see git
   history). DDNS will no-op while the flag is 1.

## Desktop setup checklist

### 1. Repo + servers

Clone the repo — the server runtimes, jars and plugins are committed.
Follow the "New machine quick start" in `docs/LOCAL_TEST_SERVER.md`
(Docker stack, run each server once, `scripts\sync-forwarding-secret.ps1`).

### 2. Cloudflare API token (one time)

Cloudflare dashboard → My Profile → API Tokens → Create Token →
template **"Edit zone DNS"** → scope: zone `glyphmc.net`.

### 3. DNS / DDNS

```powershell
[Environment]::SetEnvironmentVariable("CLOUDFLARE_API_TOKEN", "<token>", "User")
[Environment]::SetEnvironmentVariable("GLYPH_DNS_ZONE", "glyphmc.net", "User")
[Environment]::SetEnvironmentVariable("GLYPH_DNS_RECORD", "play.glyphmc.net,anarchy.glyphmc.net,smp.glyphmc.net", "User")
[Environment]::SetEnvironmentVariable("GLYPH_USE_PLAYIT", "0", "User")
scripts\cloudflare-ddns.ps1
```

Do not put `glyphmc.net` (apex) in `GLYPH_DNS_RECORD` — that CNAME is the
Pages site. Do not put `map.glyphmc.net` there either — Cloudflare Tunnel
`glyph-map` owns that hostname (`scripts\setup-bluemap-tunnel.ps1`). BlueMap
listens on `127.0.0.1:8100` only; do not forward port 8100.

Scheduled task "Glyph DDNS" every 5 minutes.

### 4. Windows Firewall

Run `scripts\setup-firewall.ps1` **as Administrator** on the desktop.
Opens TCP 25565 and UDP 24454/24455. Backend ports 25566/25567 stay closed
(localhost bind only, GDD section 38). Do not open 8100; BlueMap is tunneled.

### 5. Verify

1. On the desktop: `Test-NetConnection localhost -Port 25565`
2. From outside the LAN (phone hotspot): `anarchy.glyphmc.net` and
   `smp.glyphmc.net`
3. https://mcsrvstat.us/server/anarchy.glyphmc.net shows the MOTD when live

## Security posture (already in place)

- Velocity: `online-mode = true` (Mojang auth), login rate limit 3s,
  command + tab-complete rate limits, query protocol disabled.
- Folia backend: binds `127.0.0.1:25566`, refuses connections without the
  Velocity forwarding secret. Never port-forward 25566.
- Paper SMP: binds `127.0.0.1:25567`. Same rule — never port-forward it.
- BlueMap: binds `127.0.0.1:8100` after the tunnel script; public HTTPS is
  `map.glyphmc.net` only. Never port-forward 8100.
- Dev PostgreSQL/Redis bind `127.0.0.1` only (docker-compose).
- Keep the Windows account password-protected.
- Auto-start: the "Glyph Servers" scheduled task runs `scripts/start-all.ps1`
  at logon — Docker engine, PostgreSQL + Redis, backends, proxy, Discord
  bot (java hidden; open `start.bat` for the local ops page).
  playit is not started unless `GLYPH_USE_PLAYIT=1`.
  `cloudflared` is started if the BlueMap tunnel was installed.
  Server start scripts prefer `JAVA_HOME` (User env, JDK 25 on the desktop).
- Auto-pull: `scripts\auto-pull.ps1 -Register` creates "Glyph Auto Pull"
  (every 2 minutes). Pulls do not restart the game servers.

## Status

- [x] Domain purchased: glyphmc.net (Cloudflare, 2026-08-09)
- [x] DDNS updater script (play + anarchy + smp A records)
- [x] Firewall script ready (`scripts/setup-firewall.ps1`)
- [x] Desktop: repo cloned + servers running (2026-08-09)
- [x] Desktop: API token + DDNS env vars + scheduled task
- [x] Public WAN (not CGNAT) — playit stopped 2026-08-15
- [ ] Router: confirm DHCP reservation + TCP 25565 / UDP 24454 / UDP 24455
      to 192.168.4.24
- [ ] Outside-LAN connection verified on native public IP
- [ ] Re-run `setup-firewall.ps1` as Admin after adding UDP 24455
- [ ] BlueMap tunnel: `scripts\setup-bluemap-tunnel.ps1` (map.glyphmc.net)
