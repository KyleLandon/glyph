# World (GDD Phase 9)

Production world settings for Glyph. Borders and spawn protection match
GDD sections 8–11. Pregeneration uses [Chunky](https://hangar.papermc.io/pop4959/Chunky)
(Folia-compatible, Java 25 / MC 26.1).

## Status (desktop, 2026-08-14)

- Seed **`6944174826991112`** in `server.properties`. Regenerating requires
  stopping Folia and deleting `glyph-folia/world*` (`scripts/reset-world.ps1`).
- Overworld spawn **`-184 70 45`** (`setworldspawn`, `spawnRadius 0`) via
  `scripts/apply-world-settings.ps1`.
- Borders: `world` ±50k, `world_nether` ±6.25k, `world_the_end` ±50k.
- Spawn protection remains vanilla `spawn-protection=96` around world spawn.
- Full border pregen is still a pre–public-launch maintenance window.

## Settings

| Setting | Value | Notes |
|---------|-------|-------|
| Seed | `6944174826991112` | `level-seed` in `server.properties` |
| Overworld spawn | `-184 70 45` | Exact block; `spawnRadius 0` |
| Overworld border | ±50,000 (diameter 100,000) | GDD §9, centered at 0,0 |
| Nether border | ±6,250 (diameter 12,500) | GDD §9 |
| End border | ±50,000 (diameter 100,000) | GDD §9 |
| Spawn protection | 96 blocks | Vanilla until GlyphCore spawn module; GDD §8 |
| `max-world-size` | 50000 | Hard cap matching overworld border |

Seed only applies when a world is **first created**. To regenerate: stop the
backend, run `scripts\reset-world.ps1`, start Folia, then
`scripts\apply-world-settings.ps1`. That wipes playerdata (inventories) but
not PostgreSQL (balances / stats). The starter kit re-grants on next join.

## Apply borders + confirm

With the backend running (RCON enabled on localhost):

```powershell
scripts\apply-world-settings.ps1
```

That sets the three world borders, world spawn at `-184 70 45`, and prints
Chunky selection. Safe to re-run.

## Pregeneration

Full ±50,000 overworld pregen is launch-scale work (tens of millions of
chunks, hundreds of GB). Do it on the production host during a maintenance
window, not while players are online.

### Staging / smoke (small radius)

After Chunky loads:

```
chunky world world
chunky center -184 45
chunky radius 2500
chunky start
```

~5,000×5,000 blocks around spawn — enough to prove Chunky works and give
new players generated terrain near spawn. Pause with `chunky pause`, resume
with `chunky continue` (survives restarts).

### Launch (full border)

```
chunky world world
chunky worldborder
chunky start

chunky world world_nether
chunky worldborder
chunky start

chunky world world_the_end
chunky worldborder
chunky start
```

`chunky worldborder` locks the selection to the live border. Monitor with
`chunky progress`. Measure disk before/after (`Measure-Object` on `world/`,
or `du -sh` on Linux) and record the number in the closed-alpha notes.

### Storage expectation

Rough guide only — actual size depends on terrain:

| Radius | Approx. chunks (square) | Ballpark disk |
|--------|-------------------------|---------------|
| 2,500  | ~40k                    | low hundreds of MB |
| 10,000 | ~640k                   | a few GB |
| 50,000 | ~39M                    | tens–hundreds of GB |

Always keep a backup (`scripts\backup.ps1`) before a long pregen run.

## Expansion

Borders may grow later (GDD §10) without resetting generated terrain.
Expansion = raise the border, then `chunky` the new ring only.
