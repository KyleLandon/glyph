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
