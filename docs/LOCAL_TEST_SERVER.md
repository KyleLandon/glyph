# Local Test Network

Three runtime directories make up the local test network. Their configs,
server jars and plugins are **committed** (Paper's jar is fetched by
`scripts\setup-smp.ps1`). Not in git: world data, logs, auto-downloaded
caches (`libraries/`, `cache/`, `versions/`, LuckPerms `libs/`) and the
proxy forwarding secret (public repo).

## New machine quick start

```powershell
scripts\dev-up.ps1              # PostgreSQL + Redis (Docker)
glyph-velocity\start.bat        # once — generates forwarding.secret; stop it
glyph-folia\start.bat           # once — generates config/paper-global.yml + world; stop it
scripts\setup-smp.ps1           # Paper 26.1.2 + shared plugin jars (LuckPerms/Vault/voice)
glyph-smp\start.ps1             # once — generates config/paper-global.yml + world; stop it
scripts\sync-forwarding-secret.ps1   # wires the secret into both backends
scripts\build_glyphcore.ps1        # build + deploy glyph plugins
# now start the stack (Discord bot starts with Folia)
# or double-click start.bat for the local ops page (http://127.0.0.1:8787)
```

```
Minecraft client
      |
      v  :25565 (public)
glyph-velocity/   Velocity 4.1 proxy — auth, modern forwarding
      |                           \
      v  127.0.0.1:25566           v  127.0.0.1:25567
glyph-folia/  Folia anarchy        glyph-smp/  Paper SMP
              role: anarchy                    role: smp
              AH, bounties, faucet             claims/homes later
```

Same Postgres wallet (`$` + Glyphs). Separate worlds and inventories.
Connect to `localhost` (port 25565, through the proxy). `/server smp`
switches. Backends only listen on localhost and refuse connections without
the Velocity secret (GDD sections 37-38). See ADR-013.

## Velocity proxy (`glyph-velocity/`)

1. Download Velocity from
   [papermc.io/downloads/velocity](https://papermc.io/downloads/velocity).
2. Run once to generate `velocity.toml` + `forwarding.secret`, stop it.
3. `velocity.toml` deviations from defaults:
   - `player-info-forwarding-mode = "modern"`
   - `[servers]`: `anarchy = "127.0.0.1:25566"`, `smp = "127.0.0.1:25567"`,
     `try = ["anarchy"]`, forced hosts `anarchy`/`play` → anarchy,
     `smp.glyphmc.net` → smp
   - branded `motd`
4. Deploy `glyph-proxy` with `scripts/build_glyphcore.ps1`; start with `start.bat`.

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
5. Deploy plugins: `scripts/build_glyphcore.ps1` (builds and copies
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
| Chunky | ✔ | also on Paper SMP | World pregeneration. Folia-safe Bukkit jar. SMP uses it to fill BlueMap around spawn. |
| LuckPerms / Vault / voicechat | also on Paper SMP | — | Copied by `scripts\setup-smp.ps1`. Same Postgres; LuckPerms `server: smp`. |
| GriefPrevention | Paper SMP | — | Land claims. |
| CoreProtect | Paper SMP | — | Staff inspect/rollback. Downloaded by `setup-smp.ps1`. |
| BlueMap | Paper SMP | — | Web map (port 8100). Anarchy stays unmapped. |

# Paper SMP backend (`glyph-smp/`)

Independent Paper 26.1.2 process. GlyphCore `server.role: smp` turns off
`/bounty` combat. Playtime pay is **$5 / 15m**. `/ah` is on (its own market
— items never cross). `/bal`, `/pay`, `/glyphs`, `/stats`, `/top` still hit
the shared database. Homes, claims, `/spawn`, `/wild`, `/tpa`, warps, chest
shops, `/trade`, `/sit`, and `/claimblocks` are SMP-only.

```powershell
scripts\setup-smp.ps1            # download Paper + copy LuckPerms/Vault/voicechat
scripts\build_glyphcore.ps1      # deploy glyph-core into glyph-smp/plugins
```

`server.properties`: port `25567`, RCON `25576`, `online-mode=false`,
`server-ip=127.0.0.1`, `difficulty=normal`, small spawn protection.
Paper anti-xray (`engine-mode: 2`) is on in `config/paper-world-defaults.yml`
(overworld + nether; End off). Same on Folia. Restart required after changes.
Do not copy Folia-only jars (Chunky, JustEnoughRecipes, spark) unless you
need them; claim/home plugins go here later, not on Folia. Voice chat uses
UDP **24455** so it does not collide with Folia's 24454.

RCON is enabled on both backends for local automation only
(`enable-rcon=true`, password `glyph-dev-rcon`, Folia `25575`, SMP `25576`,
bound via `server-ip=127.0.0.1`). Use `scripts\rcon.ps1 "<command>"` or
`scripts\rcon.ps1 -Target smp "<command>"`. Never expose RCON on a public
host — leave it off in production (`docs/DEPLOYMENT.md`).

Both LuckPerms instances are configured for **shared storage on the dev
PostgreSQL** (`storage-method: postgresql`, database `glyph`, tables
`luckperms_*`) with **Redis messaging** (`messaging-service: redis`), so
permission changes propagate proxy⇄backend instantly. On a fresh machine,
apply the same edits to `plugins/LuckPerms/config.yml` (Folia, also
`server: anarchy`), `glyph-smp/plugins/LuckPerms/config.yml` (`server: smp`),
and `plugins/luckperms/config.yml` (Velocity, `server: proxy`): point `data:`
at `127.0.0.1:5432`/`glyph`/`glyph_app` and
`redis:` at `127.0.0.1:6379` with the dev passwords from `.env`.
The Docker stack (`scripts/dev-up.ps1`) must be running or LuckPerms falls
back with errors at startup.

## Daily use

Double-click **`start.bat`** in the repo root — opens the local ops page at
`http://127.0.0.1:8787` (status, logs, RCON) and starts Docker, PostgreSQL,
Redis, Folia, Paper SMP, Velocity, and Discord if they are down.

```powershell
.\start.ps1                      # same thing from a shell
scripts\build_glyphcore.ps1      # after code changes: build + stage jars
scripts\build_glyphcore.ps1 -Restart   # build, then in-game /restart
scripts\watch-glyphcore.ps1      # auto-build on save; you still /restart
```

While Folia is up, builds go to `plugins/update/` (never overwrite the live
jar). `/restart` loads the staged jar after a 10-second countdown.

Connect to `localhost` in Minecraft (or `play.glyphmc.net` from outside).
(`.bat` wrappers exist because double-clicked `.ps1` files open in an editor
instead of running.)

In game (or console): `/glyph status` shows PostgreSQL/Redis health,
`/glyph version` shows build info. Without local PostgreSQL/Redis running,
both report DOWN — the plugin still loads and the server plays fine
(GDD sections 86-87). Start them with `scripts/dev-up.ps1` (requires Docker).
