# Staging (GDD Phase 8)

Private test of the full stack before closed alpha. The desktop network
(`glyph-velocity` → `glyph-folia` → Docker Postgres/Redis) **is** the staging
environment until a public IP or VPS exists (see `PUBLIC_ACCESS.md`).

Run the automated suite:

```powershell
scripts\staging-smoke.ps1
```

Manual items (combat, deaths, voice) need a Minecraft client — check them off
below when done.

## Topology under test

```
Velocity :25565  →  Folia :25566  →  Postgres + Redis
```

## Checklist

| # | GDD item | How verified | Status |
|---|----------|--------------|--------|
| 1 | Stack boots cleanly | `staging-smoke.ps1` health checks | ✅ 2026-08-09 |
| 2 | Economy transfers / insufficient funds / concurrent pay | `PostgresEconomyRepositoryIT` | ✅ via gradle suite |
| 3 | Auction race: two buyers, one sale | `PostgresAuctionRepositoryIT.concurrentPurchaseSellsExactlyOnce` | ✅ via gradle suite |
| 4 | Bounty concurrent kill never double-pays | `PostgresBountyRepositoryIT.concurrentKillsNeverDoublePay` | ✅ via gradle suite |
| 5 | Database outage fail-soft (player join/quit, Vault, economy) | unit + IT coverage + live pause in smoke script | ✅ 2026-08-09 |
| 6 | Backend restart persistence | smoke script stop/start; GlyphCore reloads schema | ✅ 2026-08-09 |
| 7 | Crash recovery | stop-kill backend, restart; worlds + DB intact | ✅ 2026-08-09 |
| 8 | Combat / deaths | join with 2 clients, fight, confirm `/stats` + bounty if set | **manual** (alpha gate) |
| 9 | Voice chat | join with 2 clients + Simple Voice Chat mod, hear each other | **manual** (alpha gate) |
| 10 | Restart with players online | reconnect after backend restart | **manual** (alpha gate) |
| 11 | Outside connect | `anarchy.glyphmc.net` / `smp.glyphmc.net` from non-LAN | **manual** (alpha gate) |
| 12 | First-join welcome + `/top` | new account sees welcome; `/top money|kills|deaths|playtime|bounty` | ✅ code 2026-08-09 |

"Multiple simulated players" for race conditions is covered by the concurrent
integration tests (threads, not Minecraft bots). Real multiplayer soak is
Phase 10 (closed alpha).

## Manual smoke notes

### Combat / deaths (item 8)

1. Two accounts through `localhost:25565`.
2. `/bounty add <victim> 100` from the killer's pocket (needs $100).
3. Kill the victim once — killer should be paid, `/stats` updates.
4. Kill again within 60 minutes — kill logged, bounty withheld.

### Voice (item 9)

Both clients need the Simple Voice Chat mod from the
[glyph-clientmods](https://github.com/KyleLandon/glyph-clientmods) pack.
UDP 24454 must be open on the host (firewall script already does this).

## Re-running

Safe anytime. The smoke script is idempotent: it will start anything that is
down, run the suite, exercise outage + restart, and leave the stack up.
