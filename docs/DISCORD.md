# Glyph Discord companion

Discord is a companion surface on the same Glyph identity (ADR-012). The bot is
a separate process (`glyph-discord`); it is never loaded inside Folia.

## What v1 does

1. **Account linking** — `/linkdiscord` in Minecraft → `/link <code>` in Discord
2. **Role sync** — Verified + lifetime ✦ tiers (Initiate → Legend) + unlocked titles
3. **Alpha entitlement** — Glyph Alpha Discord role ↔ `player_access.alpha`
4. **Optional whitelist** — Velocity denies join unless `alpha` when enabled
5. **Staff guide** — `/staffhelp` ephemeral menu + `/staffguide setup` forum embeds

## Discord Developer Portal

1. Create an application → Bot → copy token → `GLYPH_DISCORD_TOKEN`
2. Enable privileged intents: **Server Members Intent** and **Message Content Intent**
   (role sync + plain `/link CODE` chat fallback)
3. Invite with scopes: `bot`, `applications.commands`
4. Permissions: Manage Roles, Send Messages (ephemeral slash replies)
5. **Roles** — optional. On first boot the bot creates (or reuses by name):

   - `Verified`
   - `Glyph Initiate` / `Scout` / `Blooded` / `Veteran` / `Legend`
   - Titles: `Wanderer` / `Outlaw` / `Warlord` / `Blooded` / `Bounty Hunter` / `Broker`
   - `Glyph Alpha`

   Put the bot’s role **above** those roles so it can assign them. You can still
   pin IDs with `GLYPH_DISCORD_ROLE_*` if you prefer fixed snowflakes.

## Environment / secrets.env

Minimal `secrets.env` (gitignored; also accepts `GlyphBotToken` /
`DiscordServerID` aliases):

```text
GLYPH_DISCORD_TOKEN=...
GLYPH_DISCORD_GUILD_ID=...

# Same DB/Redis as GlyphCore (defaults shown)
GLYPH_DB_HOST=localhost
GLYPH_DB_PORT=5432
GLYPH_DB_DATABASE=glyph
GLYPH_DB_USERNAME=glyph_app
GLYPH_DB_PASSWORD=
GLYPH_REDIS_HOST=localhost
GLYPH_REDIS_PORT=6379
```

Search order: `GLYPH_DISCORD_ENV_FILE`, `./secrets.env`,
`glyph-discord/secrets.env`, legacy path under `glyph-core/.../discord/secrets.env`.

## Run the bot

Starts with the game servers (`start.bat` ops page / `scripts\start-all.ps1`).
Idempotent: a second launch is skipped when the process is already up.

```powershell
scripts\start-discord.ps1
# or, foreground in this window:
glyph-discord\start.ps1
```

Flyway **V9** must already be applied by GlyphCore before the bot links anyone.

## Player flow

1. Join Minecraft → `/linkdiscord` → receive `GLYPH-XXXXXX`
2. In Discord → `/link GLYPH-XXXXXX` (ephemeral confirmation)
3. Roles update from lifetime Glyphs and unlocked titles automatically

Unlink: `/unlinkdiscord` in-game (ops: `/glyphadmin unlinkdiscord <player>`).

## Closed alpha whitelist

When ready to gate the server on Discord Alpha:

1. Grant players the **Glyph Alpha** role (or `/alpha grant @user` — requires linked account + Manage Roles)
2. Restart Velocity with:

```powershell
$env:GLYPH_DISCORD_WHITELIST = 'true'
# same GLYPH_DB_* as Folia
```

Default is **off**. Deny message points players at Discord + `/linkdiscord`.

## Staff guide

Staff (Manage Server / Manage Roles / Admin):

| Command | What it does |
| --- | --- |
| `/staffhelp` | Ephemeral index + topic dropdown (Economy, Glyphs, …) |
| `/staffhelp topic:Money` | Jump straight to one embed |
| `/staffguide setup` | Create/refresh **#staff-guide** forum posts with embeds |

Pin in staff-chat (bot suggests text after setup):

```text
Glyph Staff Guide
Use #staff-guide for the full reference.
Quick: /staffhelp · /glyph status · /eco get <player> · /stats <player>
```

## Redis events

Channel `glyph.events` (JSON):

- `glyph.lifetime` — `{uuid, lifetimeEarned}` → role sync
- `glyph.title` — `{uuid}` → title role sync
- `discord.linked` — `{uuid, discordUserId}` → role sync

Postgres remains authoritative. No Discord economy mutations in v1.

## Out of scope (later)

Server status, `/player` embeds, bounty/market feeds, reports, tickets,
companies, website OAuth, global chat bridge.
