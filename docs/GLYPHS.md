# Glyphs — Prestige Currency

**Glyphs** (`✦`) are account-bound prestige currency. They cannot be traded,
dropped, auctioned, transferred, converted to `$`, or withdrawn as items.
There is no `/glyphpay`.

Glyphs purchase **permanent account cosmetics and unlocks only**. If you can
use it to survive, fight, build, craft, travel, or make `$`, Glyphs cannot
buy it. Soft `$` remains the sole player economy.

## Philosophy

- **Earn** through achievements and milestones — not kill farming or AFK loops.
- **Spend** on flair: name colors, titles, custom death messages.
- **Display** prestige in tab list; opt-in sidebar HUD via `/glyphs hud on`.
- **Discord tiers** reflect lifetime Glyphs earned (bot sync later).

## Earn

| Source | Reward | Notes |
| --- | ---: | --- |
| First bounty claim | 3 ✦ | One-time per account |
| 10 unique player kills | 2 ✦ | First kill per victim counts once |
| 25 unique player kills | 3 ✦ | Achievement milestone |
| 50 unique player kills | 5 ✦ | Unlocks equipable title **Blooded** |
| 100 unique player kills | 10 ✦ | Achievement milestone |
| 10 bounty claims | 5 ✦ | Achievement milestone |
| 25 bounty claims | — | Unlocks equipable title **Bounty Hunter** |
| $1,000,000 lifetime AH sales | — | Unlocks equipable title **Broker** |
| `/glyphadmin` | any | Ops only |

Repeat kills of the same victim do **not** count toward unique-kill milestones.

## Discord tiers (lifetime earned — not shop)

| Lifetime ✦ earned | Tier |
| ---: | --- |
| 10 | Initiate |
| 25 | Scout |
| 50 | Blooded |
| 100 | Veteran |
| 250 | Legend |

Shown in `/glyphs` balance output, e.g. `Discord tier: Scout (lifetime 67 ✦)`.
When linked (`/linkdiscord` → Discord `/link`), GlyphDiscord syncs these as
guild roles. See `docs/DISCORD.md`.

## Shop catalog

### Name colors (unlock once → `/glyphs color <id>`)

| ID | Color | Cost |
| --- | --- | ---: |
| `name_gray` | Gray | 3 |
| `name_yellow` | Yellow | 4 |
| `name_green` | Green | 6 |
| `name_aqua` | Aqua | 6 |
| `name_blue` | Blue | 6 |
| `name_gold` | Gold | 8 |
| `name_light_purple` | Light purple | 10 |
| `name_red` | Red | 12 |
| `name_dark_purple` | Dark purple | 15 |

Default white is free.

### Titles (unlock once → `/glyphs title <id>`)

| ID | Title | Cost |
| --- | --- | ---: |
| `title_wanderer` | Wanderer | 5 |
| `title_outlaw` | Outlaw | 8 |
| `title_warlord` | Warlord | 25 |

Achievement titles (**Blooded**, **Bounty Hunter**, **Broker**) are not sold —
they unlock from milestones above.

### Death messages (unlock once → `/glyphs death <id>`)

Custom broadcast when you are killed by another player.

| ID | Style | Cost | Message |
| --- | --- | ---: | --- |
| `death_fell` | Fell before | 5 | `{victim} fell before {killer}` |
| `death_claimed` | Claimed | 8 | `{killer} claimed {victim}'s life.` |
| `death_silence` | Silence | 12 | `{victim} was silenced by {killer}.` |
| `death_glyph` | Glyph | 15 | `{killer} etched {victim} into Glyph history.` |

## Commands

```
/glyphs                         balance, lifetime earned, discord tier, equipped flair
/glyphs shop                    catalog
/glyphs buy <id>                purchase
/glyphs color <id|none>         equip unlocked name color
/glyphs title <id|none>         equip unlocked title (shop or achievement)
/glyphs death <id|none>         equip death message style
/glyphs unlocks                 what you own
/glyphs hud on|off              per-player sidebar HUD preference

/glyphadmin get <player>
/glyphadmin set <player> <amount>
/glyphadmin add <player> <amount>
/glyphadmin remove <player> <amount>
```

## Display

- **Tab list:** `[Title] Name  $12.4K  ✦13  ☠29` — title in gray brackets,
  name in equipped color, compact cash, Glyphs in light purple, deaths in red
  with ☠ before the count.
- **Sidebar HUD:** opt-in via `/glyphs hud on`. Shows green `$` line and
  light-purple `✦` line when enabled. `economy.hud.enabled` in config remains
  the global kill-switch.

## Config (`config.yml`)

```yaml
glyphs:
  enabled: true
  symbol: "✦"
  first-bounty-reward: 3
```
