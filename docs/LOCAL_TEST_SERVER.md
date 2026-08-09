# Local Test Network

Two runtime directories make up the local test network. Their configs,
server jars and plugins are **committed**, so a fresh clone runs with
minimal setup. Not in git: world data, logs, auto-downloaded caches
(`libraries/`, `cache/`, `versions/`, LuckPerms `libs/`) and the proxy
forwarding secret (public repo).

## New machine quick start

```powershell
scripts\dev-up.ps1              # PostgreSQL + Redis (Docker)
glyph-velocity\start.bat        # once — generates forwarding.secret; stop it
glyph-folia\start.bat           # once — generates config/paper-global.yml + world; stop it
scripts\sync-forwarding-secret.ps1   # wires the secret into the backend
scripts\deploy-local.ps1        # build + deploy glyph plugins
# now start both servers normally
```

```
Minecraft client
      |
      v  :25565 (public)
glyph-velocity/   Velocity 4.1 proxy — auth, modern forwarding
      |
      v  127.0.0.1:25566 (private)
glyph-folia/      Folia 26.1.2 backend — online-mode=false, forwarding secret
```

Connect to `localhost` (port 25565, through the proxy). The backend only
listens on localhost and refuses connections without the Velocity secret
(GDD sections 37-38).

## Velocity proxy (`glyph-velocity/`)

1. Download Velocity from
   [papermc.io/downloads/velocity](https://papermc.io/downloads/velocity).
2. Run once to generate `velocity.toml` + `forwarding.secret`, stop it.
3. `velocity.toml` deviations from defaults:
   - `player-info-forwarding-mode = "modern"`
   - `[servers]`: `anarchy = "127.0.0.1:25566"`, `try = ["anarchy"]`
     (example servers and forced-hosts removed)
   - branded `motd`
4. Deploy `glyph-proxy` with `scripts/deploy-local.ps1`; start with `start.bat`.

The backend gets the *contents* of `forwarding.secret` in
`config/paper-global.yml` under `proxies.velocity.secret` (see below).

# Folia backend (`glyph-folia/`)

## Recreate from scratch

1. Download the latest **stable** Folia jar from
   [papermc.io/downloads/folia](https://papermc.io/downloads/folia) into
   `glyph-folia/`.
2. Run it once (`java -jar folia-<version>.jar --nogui`), accept `eula.txt`.
3. Apply the `server.properties` values below.
4. Copy `start.ps1` (below) or use the committed one if present.
5. Deploy plugins: `scripts/deploy-local.ps1` (builds and copies
   `glyph-core`). `spark` is also installed for profiling (GDD section 74).
6. Start, then run the one-time commands below in the console.

## server.properties (deviations from defaults)

| Key                   | Value  | Why (GDD)                                            |
|-----------------------|--------|------------------------------------------------------|
| `difficulty`          | `hard` | The server should feel dangerous (section 2)         |
| `max-world-size`      | `50000`| ±50,000 overworld border cap (section 9)             |
| `spawn-protection`    | `96`   | 64-128 block spawn protection (section 8) — vanilla mechanism until GlyphCore's spawn module replaces it |
| `view-distance`       | `8`    | Performance headroom for dev (section 74)            |
| `simulation-distance` | `8`    | Performance headroom for dev (section 74)            |
| `motd`                | Glyph branding | —                                            |
| `server-ip`           | `127.0.0.1` | Backend binds localhost only — never public (section 38) |
| `server-port`         | `25566` | Private port behind Velocity                        |
| `online-mode`         | `false` | Velocity authenticates; backend trusts forwarding    |

`config/paper-global.yml`: `proxies.velocity.enabled: true`,
`online-mode: true`, `secret:` = contents of the proxy's `forwarding.secret`.

Kept defaults worth knowing: `white-list=false`, `gamemode=survival`.

## One-time console commands (per world)

World borders (GDD section 9) — prefer the script (uses RCON):

```powershell
scripts\apply-world-settings.ps1
```

Or by hand (`worldborder set` takes the *diameter*):

```
worldborder set 100000
execute in minecraft:the_nether run worldborder set 12500
execute in minecraft:the_end run worldborder set 100000
```

See `docs/WORLD.md` for seed, Chunky pregen, and storage notes.

Gamerules — since MC 26.1 these replaced old `server.properties` keys and are
per-world. Defaults are already correct for anarchy (`minecraft:pvp` = true),
listed here for reference:

```
gamerule minecraft:pvp true
```

Op yourself for testing:

```
op <your-name>
```

## Third-party plugins

| Plugin | Folia | Velocity | Purpose |
|--------|-------|----------|---------|
| LuckPerms 5.5.x | `LuckPerms-Bukkit` | `LuckPerms-Velocity` | Permissions. **Use the Bukkit jar on Folia — the Fabric jar is a different mod loader and silently never loads.** |
| Simple Voice Chat | `voicechat-bukkit` | `voicechat-velocity` | Proximity voice (GDD section 2). Version numbers differ per platform — that is normal. |
| VaultUnlocked | ✔ | — | Economy API bridge. GlyphCore registers `GlyphEconomy` as the Vault provider, so third-party plugins read/write Glyph balances (ledgered as SYSTEM_REWARD/SYSTEM_SINK). |
| ViaVersion + ViaBackwards | — | ✔ | Newer/older clients can join through the proxy. |
| spark | ✔ | — | Profiling (GDD section 74). |
| Chunky | ✔ | — | World pregeneration (GDD Phase 9). Folia-safe Bukkit jar. |

RCON is enabled on the Folia backend for local automation only
(`enable-rcon=true`, password `glyph-dev-rcon`, port `25575`, bound via
`server-ip=127.0.0.1`). Use `scripts\rcon.ps1 "<command>"`. Never expose
RCON on a public host — leave it off in production (`docs/DEPLOYMENT.md`).

Both LuckPerms instances are configured for **shared storage on the dev
PostgreSQL** (`storage-method: postgresql`, database `glyph`, tables
`luckperms_*`) with **Redis messaging** (`messaging-service: redis`), so
permission changes propagate proxy⇄backend instantly. On a fresh machine,
apply the same edits to `plugins/LuckPerms/config.yml` (Folia, also
`server: anarchy`) and `plugins/luckperms/config.yml` (Velocity,
`server: proxy`): point `data:` at `127.0.0.1:5432`/`glyph`/`glyph_app` and
`redis:` at `127.0.0.1:6379` with the dev passwords from `.env`.
The Docker stack (`scripts/dev-up.ps1`) must be running or LuckPerms falls
back with errors at startup.

## Daily use

```powershell
scripts\dev-up.ps1        # PostgreSQL + Redis (Docker)
scripts\deploy-local.ps1  # build + deploy both plugins
```

Then start `glyph-folia\start.bat` (backend) and `glyph-velocity\start.bat`
(proxy) — order does not matter; Velocity retries the backend on join.
Connect to `localhost` in Minecraft. (`.bat` wrappers exist because
double-clicked `.ps1` files open in an editor instead of running.)

In game (or console): `/glyph status` shows PostgreSQL/Redis health,
`/glyph version` shows build info. Without local PostgreSQL/Redis running,
both report DOWN — the plugin still loads and the server plays fine
(GDD sections 86-87). Start them with `scripts/dev-up.ps1` (requires Docker).
