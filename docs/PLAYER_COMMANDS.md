# Glyph — Player Commands & Features

Player-facing reference for **play.glyphmc.net**. Reuse this on Discord, the
website, and in-game intros. Staff-only commands are listed separately at the
bottom.

Amounts are **whole dollars** (no cents). Example: `/pay Steve 50`

---

## Server rules of the road

Glyph is **persistent anarchy + economy**:

| Feature | Status |
| --- | --- |
| Land claims / region protection | **None** — bases can be griefed or raided |
| Item recovery by staff | **None** — deaths and theft stick |
| Free teleports / homes / warps | **None** (beyond spawn) |
| Spawn area | Small protected zone only (~96 blocks) — no building/breaking there |
| Economy | Player-driven: auctions, bounties, payments, playtime rewards |
| Voice | Proximity voice chat (optional client mod) |

Trust carefully. Build smart. Make money.

---

## Money

| Command | What it does |
| --- | --- |
| `/balance` or `/bal` | Your cash |
| `/balance <player>` | Another player's balance (if permitted) |
| `/pay <player> <amount>` | Send money to a player |
| `/baltop` | Richest players |
| `/money history` | Your recent transactions |

**How you earn**

- **$100** on first join (one-time starter)
- **~$10 per 15 minutes** of *active* play (moving, mining, building — AFK does not pay)
- Selling on the auction house
- Collecting bounties by killing wanted players
- Getting paid by other players

Cash also shows on the **right-side HUD** and in the **Tab** player list.

---

## Auction house

| Command | What it does |
| --- | --- |
| `/ah` | Open the auction house GUI |
| `/ah sell <price>` | List the item in your hand |
| `/ah search <text>` | Search listings |
| `/claim` or `/mail` | Collect bought items / returned listings |

Aliases: `/auction`, `/auctionhouse`

**Fees (sinks):** ~1% listing fee when you post, ~5% sale fee from the seller’s proceeds. Listings expire after **48 hours**; unsold items come back through `/claim`.

---

## Bounties

| Command | What it does |
| --- | --- |
| `/bounty` | Most-wanted board |
| `/bounty <player>` | Total bounty on a player |
| `/bounty add <player> <amount>` | Place a bounty (money escrowed) |

Minimum bounty: **$100**. Kill the target to claim the pot (anti-farm cooldowns apply).

---

## Stats

| Command | What it does |
| --- | --- |
| `/stats` | Your kills, deaths, blocks, playtime, etc. |
| `/stats <player>` | Someone else’s stats |
| `/playtime` | Your accumulated online time |
| `/top <category>` | Leaderboards: `money`, `kills`, `deaths`, `playtime`, `bounty` |

Tab list also shows each online player’s **money** and **death count**.

---

## Voice chat & client mods

Voice is **proximity** (Simple Voice Chat). Not required to join.

Optional modpack: [glyph-clientmods](https://github.com/KyleLandon/glyph-clientmods) — voice, minimap/world map, performance, JEI, etc.

Tip: move Xaero’s minimap to the **left** (`Y` → Change Position) so it does not cover the cash HUD on the right.

---

## Quick start

1. Join `play.glyphmc.net` — you start with **$100**.
2. Leave spawn, gather gear, make a hidden base (nothing protects it but distance and secrecy).
3. `/ah sell <price>` to list loot; `/ah` to browse and buy.
4. `/claim` when you have mail.
5. `/bounty add <player> <amount>` if you want someone hunted.
6. Stay active to earn playtime pay; `/bal` / Tab / HUD to watch your cash.

---

## Staff only

Not for normal players:

| Command | What it does |
| --- | --- |
| `/eco get <player>` | Inspect balance |
| `/eco set <player> <amount>` | Set balance |
| `/eco add <player> <amount>` | Add money |
| `/eco remove <player> <amount>` | Remove money |
| `/glyph status` | Infrastructure health |
| `/glyph version` | Plugin version |
| `/tps` | Region tick health (ops) |

---

## Discord / website copy-paste (short)

```
GLYPH — Commands
play.glyphmc.net

No land claims. No grief protection. Your base can burn.

Money
 /bal  /pay <player> <amount>  /baltop  /money history
 Starter $100 · active playtime pays · HUD + Tab show cash

Auction
 /ah  ·  /ah sell <price>  ·  /ah search <text>  ·  /claim

Bounties
 /bounty  ·  /bounty <player>  ·  /bounty add <player> <amount>  (min $100)

Stats
 /stats  ·  /stats <player>  ·  /playtime  ·  /top <money|kills|deaths|playtime|bounty>

Voice: Simple Voice Chat (optional) — pack: github.com/KyleLandon/glyph-clientmods

Discord: https://discord.gg/htkQHR4gdf
Site: https://glyphmc.net
```
