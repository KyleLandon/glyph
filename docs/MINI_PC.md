# Moving the network to the mini PC

Target: a headless Windows mini PC (16 GB RAM, 500 GB SSD) on the same LAN
and public IP as the desktop. The stack stays exactly as it is today
(Docker PostgreSQL + Redis, Folia, Paper SMP, Velocity, Discord bot,
cloudflared for the map). Only the machine changes.

## RAM budget (16 GB)

| Piece | Heap / cap | Real use |
|---|---|---|
| Folia (anarchy) | 4 GB, pre-touched | ~4.7 GB |
| Paper SMP | 4 GB, pre-touched | ~4.7 GB |
| Velocity | 512 MB | ~0.8 GB |
| Discord bot | JVM default | ~0.3 GB |
| Docker (WSL2 VM: Postgres + Redis) | **cap at 2 GB** | ~1-2 GB |
| Windows | | ~3 GB |

That is ~14-15 GB, so it fits but is tight. **Cap the WSL VM**, or it will
take up to half the RAM for page cache. Create `%USERPROFILE%\.wslconfig`:

```ini
[wsl2]
memory=2GB
swap=2GB
```

then `wsl --shutdown` and restart Docker Desktop. If Task Manager shows
memory pressure under load, drop SMP's `$mem` in `glyph-smp/start.ps1` to 3G.

## Disk (500 GB)

Worlds are ~19 GB (Folia 15 GB, SMP 4 GB) plus BlueMap tiles. A nightly
backup is ~15 GB now that it includes the SMP world, so the 7-day retention
`register-tasks.ps1` sets uses ~105 GB. Backups land in `glyph-backups\`
next to the repo (e.g. `C:\glyph-backups`). A second disk or a NAS copy is
still a good idea: backups on the same SSD do not survive the SSD dying.

## 1. Prepare the mini PC (no downtime)

Windows setup for headless running:

- **Auto sign-in.** Docker Desktop and the "Glyph Servers" task start at
  logon, so the machine must sign itself in after a reboot. Use Sysinternals
  **Autologon** (`winget install Microsoft.Sysinternals.Autologon`). It stores
  the password encrypted.
- **Never sleep.** Run as Admin:
  `powercfg /change standby-timeout-ac 0; powercfg /change hibernate-timeout-ac 0; powercfg /hibernate off`
- **BIOS:** set "Restore on AC power loss" (or "Power On after power fail")
  to **On**, so a power cut ends in a reboot, not a dead box.
- **Windows Update:** set active hours. Update reboots are fine: auto sign-in
  plus the logon task bring the network back.
- **Remote access.** Windows *Home* cannot host Remote Desktop. Use Tailscale
  (plus the built-in OpenSSH Server: `Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0`)
  or RustDesk / Chrome Remote Desktop. On *Pro*, RDP works. **Disconnect**
  from RDP rather than signing out, because signing out stops the servers.
  The ops page (`127.0.0.1:8787`) is localhost-only. Reach it with
  `ssh -L 8787:127.0.0.1:8787 <minipc>` or from a remote-desktop session.
- **Router:** give the mini PC a DHCP reservation (static LAN IP).

Install:

- Git for Windows
- JDK 25 (Temurin). Set `JAVA_HOME` as a *User* env var.
- Docker Desktop (WSL2 backend). Turn on "Start Docker Desktop when you sign
  in". Add the `.wslconfig` above.

Clone (no GitHub login needed: the repo is public):

```powershell
git clone https://github.com/KyleLandon/glyph.git C:\glyph
```

The client modpack repo is for players. The server does not need it.

## 2. Cutover (downtime starts)

On the **desktop**, in an Administrator PowerShell, with a USB drive or
network share mounted:

```powershell
K:\glyph\scripts\migrate-export.ps1 -Destination E:\glyph-migration -Retire
```

This stops Velocity, saves and stops both backends over RCON, stops the bot,
dumps PostgreSQL, and copies worlds, BlueMap, CoreProtect, SMP jars, secrets,
`~\.cloudflared` and the DDNS env vars into the bundle (~20 GB).
`-Retire` disables the desktop's Glyph tasks and cloudflared service so it
does not come back after a reboot.

Copy `E:\glyph-migration` to the mini PC. Then on the **mini PC**, with
Docker Desktop running:

```powershell
C:\glyph\scripts\migrate-import.ps1 -Source E:\glyph-migration
```

Then in an **Administrator** PowerShell on the mini PC:

```powershell
C:\glyph\scripts\register-tasks.ps1          # Servers, DDNS, Auto Pull, Backup
C:\glyph\scripts\setup-firewall.ps1          # TCP 25565, UDP 24454/24455
C:\glyph\scripts\setup-bluemap-tunnel.ps1    # reuses the glyph-map tunnel, installs the service
C:\glyph\scripts\start-all.ps1
```

**Router:** move the port forwards (TCP 25565, UDP 24454, UDP 24455) from
the desktop's IP to the mini PC's IP. The public IP does not change, so DNS
needs no update. DDNS just keeps running from the new box.

## 3. Verify

1. `Test-NetConnection localhost -Port 25565` on the mini PC.
2. Join `play.glyphmc.net` from outside the LAN (phone hotspot). Check that
   your rank works (LuckPerms reads Postgres) and your balance is there.
3. https://map.glyphmc.net loads.
4. **Reboot the mini PC and touch nothing.** Within ~5 minutes it should be
   joinable again. If not, check the "Glyph Servers" task history and
   `docker ps`.
5. The next morning, check that a backup appeared in `C:\glyph-backups`.

Once it has run cleanly for a few days, delete the migration bundle (it holds
secrets) and old backups on the desktop (`K:\glyph-backups`).

## Port 5432 on the desktop

Since 2026-10-21 the desktop's `glyph-dev-postgres` container has not been
running: another project's `operatoros-db` container holds port 5432, so
LuckPerms and GlyphCore have been failing auth against the wrong database.
The Glyph data is intact in the `glyph-dev_glyph-postgres-data` volume, and
`migrate-export.ps1` dumps it straight from the volume. On the mini PC, keep
5432 free for Glyph.
