# Architecture Decision Log

The GDD (`docs/GDD.md`) demands that architecture deviations be documented
(section 1, rule 2). This file is that record.

## ADR-001: Project naming — `glyph-*` instead of `anarchy-*`

**Status:** accepted (2026-08-09)

The GDD uses `anarchy-*` module names, `com.example.anarchy*` packages and
`ANARCHY_*` environment variables as placeholders. The repository owner branded
the project **Glyph**, so:

- Modules: `glyph-api`, `glyph-core`, `glyph-proxy`
- Packages: `com.glyph.api`, `com.glyph.core`, `com.glyph.proxy`
- Plugins: `GlyphCore` (Folia), `GlyphProxy` (Velocity)
- Environment variables: `GLYPH_*` (e.g. `GLYPH_DB_HOST` replaces `ANARCHY_DB_HOST`)

Semantics are otherwise identical to the GDD.

## ADR-002: Folia API pinned to 26.1.2 (stable) instead of 26.2

**Status:** accepted (2026-08-09)

The GDD targets Minecraft 26.2, but as of today Folia publishes 26.2 only as
beta builds (`26.2.build.1-beta`); the latest stable is `26.1.2.build.8-stable`.
Per the GDD priority order (correctness → stability first), we build against
the stable API. Revisit when Folia 26.2 goes stable — bump
`folia-api` in `gradle/libs.versions.toml` and `api-version` in `plugin.yml`.

## ADR-003: Runtime libraries via `plugin.yml libraries:` instead of shading

**Status:** accepted (2026-08-09)

HikariCP, Flyway, the PostgreSQL driver and Lettuce are declared in the
`libraries:` section of `plugin.yml`. Paper/Folia resolves them from Maven
Central at startup and isolates them per-plugin.

Rationale: shading Flyway is fragile (classpath scanning + resource
relocation), and library isolation avoids conflicts with other plugins.

Exception: the `glyph-api` classes ARE bundled into the `glyph-core` jar
(Paper knows nothing about our Gradle modules, and the API must exist at
runtime for GlyphCore and any consuming plugin).
Trade-off: first server start needs internet access to Maven Central. If that
ever becomes unacceptable for production, switch to a shaded jar with
relocation and re-test migrations.

## ADR-004: Java 25 LTS toolchain

**Status:** accepted (2026-08-09)

GDD requires Java 21+. We build with Java 25 (current LTS, installed on the
dev machine, matches current Paper-ecosystem tooling). Run Folia and Velocity
on Java 25 in all environments.

## ADR-005: V1 migration includes economy tables ahead of Phase 3

**Status:** accepted (2026-08-09)

`V1__initial_schema.sql` creates `players`, `accounts` and `transactions`
exactly as specified in GDD sections 49-51, even though the economy service
arrives in Phase 3. The schema is the GDD's own definition; having it from V1
avoids churn and lets integration tests validate the real schema now. Later
phases add tables via new numbered migrations only.

## ADR-006: Player identity skips (not queues) writes during database outages

**Status:** accepted (2026-08-09)

GDD section 133 requires database outages to be handled gracefully. During an
outage, `PlayerService` logs and **skips** join/quit persistence instead of
queueing writes for replay. Rationale:

- The join upsert is self-healing: the next successful join recreates or
  refreshes the row (and the economy account insert is idempotent).
- Timestamps come from the PostgreSQL clock (`now()`), so replayed writes
  would carry wrong times anyway.
- A replay queue is real complexity (persistence, ordering, dedup) for data
  that is not money. Phase 3 economy writes will get stronger guarantees.

Cost: playtime/last-seen from sessions that end mid-outage is lost. Accepted.

## ADR-007: Timestamps come from the database clock

**Status:** accepted (2026-08-09)

`first_join`, `last_join`, `last_seen` and `updated_at` are written with
PostgreSQL `now()`, never with JVM time. Multiple backend servers will share
one database; using its clock makes cross-server timestamps consistent and
monotonic per transaction. Only session *durations* (playtime) are measured
on the game server.

## ADR-008: Money HUD is a right-side scoreboard sidebar

**Status:** accepted (restored DonutSMP-style layout 2026-08-09);
decimal-places part superseded by ADR-010

Cash is shown on the vanilla scoreboard sidebar: white server name
(`economy.hud.title`, default `GLYPH`) above a single green compact line
(`$ 100`, `$ 12.5K`, `$ 1.6M`). Score digits are hidden via Paper's blank
NumberFormat. Updates are event-driven from EconomyService balance
notifications — no polling.

Trade-off: client minimaps (Xaero in the Glyph client pack) also use the
right edge and can cover the sidebar. Players should move the minimap left
(`Y → Change Position`). GlyphCore owns the player's scoreboard; future
sidebar lines extend MoneyHud rather than registering a second board.

## ADR-009: Vault bridge runs blocking SQL on the calling thread

**Status:** accepted (2026-08-09)

VaultUnlocked lets third-party plugins (shops, jobs) use the Glyph economy,
but classic Vault's API is synchronous while ours is async. The bridge
(`VaultEconomyBridge`) executes the same locked single-row SQL transactions
directly on the calling thread instead of block-waiting on the async executor:

- Local queries are single-digit milliseconds; an occasional Vault call on a
  region thread is tolerable, a deadlock-prone wait is not.
- When the database is down every call fails fast (no pool access), so an
  outage cannot stall tick threads.
- Deposits mint as SYSTEM_REWARD, withdrawals burn as SYSTEM_SINK, reason
  "vault bridge" — the money supply stays measurable (GDD section 122).
- Successful mutations are pushed through EconomyService listeners so the
  money HUD updates like any other change.

Rule: Vault is for third-party compatibility only. Trusted Glyph plugins use
the async `GlyphApi.economy()`. Banks are unsupported; worlds are ignored
(one economy per network).

## ADR-010: The economy has no cents — whole dollars only

**Status:** accepted (2026-08-09), supersedes the decimal part of ADR-008

Prices in a Minecraft economy are whole-dollar in practice, and carrying
cents through every amount (`priceMinor`, `10000 = $100.00`) made code and
config harder to read for zero gameplay value. The smallest unit of money is
now $1:

- `Money` wraps BIGINT whole dollars; parsing rejects decimals; formatting
  is `$1,234` with no decimal point.
- Database BIGINT columns hold dollars (V6 migration divided existing
  amounts by 100).
- Config amounts are plain dollars (`starting-balance: 100`), no `-minor`
  suffixes.
- The Vault bridge reports `fractionalDigits() = 0` and rounds fractional
  deposits/withdrawals from third-party plugins half-up to whole dollars.
- Auction fee percentages remain basis points; `ceilDiv` keeps every nonzero
  fee at least $1, so fees are never free.

## ADR-011: Glyphs prestige currency (✦)

**Status:** accepted (2026-08-09), revised (prestige v2)

Glyphs are a separate, non-convertible, account-bound prestige currency stored
on `accounts.glyphs_balance` with their own `glyph_ledger` and `glyph_unlocks`
tables (migrations V7, V8). They cannot be traded, dropped, auctioned,
transferred, converted to `$`, or withdrawn as items. There is no `/glyphpay`.

**Spend:** permanent account cosmetics only — name colors, titles, and custom
death messages via `/glyphs shop`. If it helps you survive, fight, build, craft,
travel, or make `$`, Glyphs cannot buy it. Soft `$` remains the sole player
economy. Removed from v1: smithing trim templates and purchasable Discord roles.

**Earn:** achievement milestones, not kill farming. Unique player kills
(`glyph_unique_kills`), bounty claims, and lifetime auction-house sales unlock
Glyph credits and titles. First bounty pays 3 ✦ (configurable). Repeat kills
of the same victim do not count.

**Discord tiers** (Initiate → Legend) derive from `glyphs_lifetime_earned`, not
shop purchases. Synced by GlyphDiscord when the account is linked (ADR-012).

**Display:** tab rows `[Title] Name $12.4K ✦13 ☠29`. Untitled players show
`[Peasant]` (display-only; not unlocked, not Discord-synced). Cash sidebar always
shows when `economy.hud.enabled`. The ✦ sidebar line is per-player opt-in
(`players.glyph_hud_enabled`, `/glyphs hud on|off`).

See `docs/GLYPHS.md`.

## ADR-012: Glyph Discord companion

**Status:** accepted (2026-08-09)

Discord is a companion surface on the same Glyph identity, not a generic chat
mirror. A separate process (`glyph-discord`, JDA) shares PostgreSQL with
GlyphCore and consumes Redis pub/sub events. JDA is never loaded inside Folia.

**Identity:** `/linkdiscord` issues a short-lived single-use code; Discord
`/link <code>` binds `minecraft_uuid` ↔ `discord_user_id` (`discord_links`,
migration V9). Codes live in `discord_link_codes` (10-minute TTL).

**Role sync:** Verified on link; prestige roles from lifetime ✦ earned
(Initiate → Legend); unlocked titles as matching Discord roles. Spending
Glyphs never demotes a Discord tier. Unequipping a title in-game does not
remove the Discord title role — unlocks are permanent badges.

**Whitelist path:** Discord role **Glyph Alpha** (or `/alpha grant`) sets
`player_access.alpha`. GlyphProxy may deny login when
`GLYPH_DISCORD_WHITELIST=true` unless `alpha` is true. Default off.

**Redis channel** `glyph.events`: `glyph.lifetime`, `glyph.title`,
`discord.linked` (more types later for bounties/status). Postgres remains
authoritative.

**Out of v1:** global chat bridge, Discord economy mutations, server-status /
market / bounty feeds, tickets, companies, website OAuth.

See `docs/DISCORD.md`.

## ADR-013: Two backends — Folia anarchy + Paper SMP

**Status:** accepted (2026-08-15)

Anarchy and SMP are **separate Minecraft processes**, not two worlds in one
Folia. One process cannot honestly run two rule-sets (claims vs no claims,
bounty combat vs none) without leaking inventory, playerdata, and gamerules.

| Shared (same UUID, same Postgres) | Split |
| --- | --- |
| Cash (`accounts`) | Worlds, inventory, ender chest, playerdata |
| Glyphs + unlocks + equipped title/color | Homes (`/sethome`) and claims (GriefPrevention) |
| Discord link | Combat, `/bounty` |
| `/bal` `/pay` `/glyphs` `/stats` `/top` | Playtime faucet, anarchy starter kit |
| Two `/ah` markets (same fees, same `$`) | Listings and `/ah mail` — items never cross |

**Anarchy** stays Folia (`glyph-folia`, `:25566`, `server.role: anarchy`) so
existing Folia plugins (Chunky, voicechat, etc.) keep working.

**SMP** (player-facing: **Forever World**) is Paper (`glyph-smp`, `:25567`,
`server.role: smp`) — a hang-out / build world, not a second anarchy. The
usual claim/home plugin pack can load later. GlyphCore uses Bukkit schedulers when Folia
region APIs are absent. Bounties stay anarchy-only. Playtime pay is on both
worlds ($25 / 15m anarchy, $5 / 15m Forever World) so building is not broke
and anarchy stays the real faucet. Each backend
has its own auction house (`auction_listings.market` / `deliveries.market`);
browse, buy, expire, and `/ah mail` are scoped so anarchy items cannot appear
on SMP and the other way around.

Velocity `try = ["anarchy"]`. Forced hosts: `anarchy.glyphmc.net` and
`play.glyphmc.net` → anarchy, `smp.glyphmc.net` → smp. Public A records
on port 25565 (no playit). `/server smp` still works after login.
LuckPerms uses the same Postgres with `server: smp`.

Do not put both rule-sets in one Folia. See `docs/LOCAL_TEST_SERVER.md`.

## ADR-014: Forever World hang-out loop

**Status:** accepted (2026-08-15)

Forever World is the build/hang-out backend, so GlyphCore owns the social
loop that anarchy refuses: `/spawn`, `/wild`, `/tpa`, `/sit`, `/warp`,
`/shop`, `/trade`, `/claimblocks`, `/mapimage`, one-player sleep, and an
armor-stand editor. Money movement (warp listing, claim-block packs, shops,
trades) goes through the Glyph ledger (`SYSTEM_SINK` / player transfers).
GriefPrevention stays the claim plugin; GlyphCore talks to it by reflection.

**Not reimplemented:** CoreProtect (staff rollback) and BlueMap (web map).
Those stay third-party, SMP-only, never on Folia anarchy.

**Spawn market** is an admin command (`/glyph market build`), not a generated
world. Staff still assign stalls with GP trust.
