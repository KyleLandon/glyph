# Deployment (GDD Phase 7)

How to run the Glyph stack in production containers, and how backups work
both there and on the current desktop deployment.

Status: the network currently runs on the host desktop (see
`LOCAL_TEST_SERVER.md` and `PUBLIC_ACCESS.md`) because the fiber ISP has us
behind CGNAT. This directory is ready for the day it moves to a VPS or the
ISP hands over a public IP and we want reproducible infra at home.

## Topology

```
internet
   |
velocity :25565/tcp          (auth, forwarding)  glyph/velocity image
   |
folia    internal :25565     (the game)          glyph/folia image
   |                :24454/udp voice, published directly
postgres / redis    internal only
```

## Quick start

```bash
cd docker
cp .env.example .env      # fill in passwords, set EULA=true
docker compose -f docker-compose.prod.yml up -d --build
```

Everything stateful lives in named volumes (`glyph-postgres-data`,
`glyph-redis-data`, `glyph-folia-data`, `glyph-velocity-data`). Images are
disposable and rebuild from pinned server versions — bump the `ARG`s in the
Dockerfiles together with the jars used in `glyph-folia/`/`glyph-velocity/`.

## First boot

1. Let both servers generate their config, then stop the stack.
2. In the `glyph-velocity-data` volume: point `velocity.toml` at
   `folia:25565`, set `player-info-forwarding-mode = "modern"`, and enable
   `force-key-authentication`.
3. Copy `forwarding.secret` from the velocity volume into the folia volume's
   `config/paper-global.yml` (`proxies.velocity.secret`) and set
   `online-mode=false` in `server.properties` — same procedure
   `scripts/sync-forwarding-secret.ps1` automates on the desktop.
4. Deploy plugin jars into `/data/plugins` of each volume (GlyphCore +
   LuckPerms + voice chat on folia; glyph-proxy + SimpleVoiceChat velocity
   module on velocity). Plugins are not baked into images, so updating one
   is a file copy + container restart.
5. `docker compose up -d` again. GlyphCore reads database/redis settings
   from the `GLYPH_*` environment the compose file injects, so its yml
   configs need no editing.

## Healthchecks

- postgres: `pg_isready`
- redis: authenticated `PING`
- folia / velocity: TCP connect to the Minecraft port (start period 120s/60s
  to ride out world loading)

`docker compose ps` shows health; `restart: unless-stopped` brings the stack
back after host reboots.

## Backups

| Where | Script | What it captures |
| --- | --- | --- |
| Desktop (now) | `scripts/backup.ps1` | `pg_dump` of the dev database + zip of `glyph-folia/world*` |
| Containers | `docker/backup.sh` | `pg_dump` + tar of the whole folia data volume |

Both use PostgreSQL custom format (`-Fc`, compressed, restorable with
`pg_restore`) and prune backups older than 14 days (configurable).

Desktop: run `scripts\backup.ps1 -Register` once to create the daily 04:30
"Glyph Backup" scheduled task. Backups land in `K:\glyph-backups`.

Production: cron the shell script, e.g.
`30 4 * * * /opt/glyph/docker/backup.sh >> /var/log/glyph-backup.log 2>&1`.

Restore:

```bash
# database (drops and recreates objects)
docker exec -i glyph-postgres pg_restore -U glyph_app -d glyph \
    --clean --if-exists < glyph-db-<stamp>.dump

# world: stop folia first, then extract into the volume / server folder
```

Live-copy world backups are almost always fine, but take one with the
server stopped before anything risky (migrations, version bumps).

## Discord companion bot

`glyph-discord` is a separate JVM process (not a Folia/Velocity plugin).
Locally it starts with Folia / `scripts\start-all.ps1`. Manual:

```powershell
scripts\start-discord.ps1
```

Closed-alpha whitelist (optional): set `GLYPH_DISCORD_WHITELIST=true` on the
Velocity host so GlyphProxy requires `player_access.alpha`. Full setup:
`docs/DISCORD.md`.

## Firewall

Only two ports face the internet: **25565/tcp** (velocity) and
**24454/udp** (voice). The database and redis are never published.

Do **not** enable RCON on a public host. The desktop staging server uses
localhost-only RCON (`scripts/rcon.ps1`) for automation; production images
leave RCON off.

- Windows host: `scripts/setup-firewall.ps1` (run as admin).
- Linux host: `ufw allow 25565/tcp && ufw allow 24454/udp`.
- DNS/DDNS, router, and CGNAT notes: `docs/PUBLIC_ACCESS.md`.
