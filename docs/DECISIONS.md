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

## ADR-008: Money is fixed at two decimal places; HUD uses the scoreboard sidebar

**Status:** superseded by ADR-010 for the decimal-places part (2026-08-09);
HUD decision still stands

The GDD's sample config exposes `decimal-places`. We hardcode two decimals
(minor units are cents) because making `Money` parse/format variable-precision
buys nothing until a design need exists, and the database comment ("BIGINT
minor units (cents)") already assumes it. Only the currency symbol is
configurable.

The requested FiveM-style money display is a per-player scoreboard sidebar
(the Minecraft equivalent of GTA RP's top-right cash HUD): title + one green
cash line, score numbers hidden via Paper's blank NumberFormat. It updates
event-driven from EconomyService balance notifications — no polling, no
periodic database reads. Known trade-off: GlyphCore owns the player's
scoreboard; if a future feature needs sidebar lines, it extends MoneyHud
rather than registering its own scoreboard.

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
