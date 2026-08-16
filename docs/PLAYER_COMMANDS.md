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

There is also a **Forever World** (`smp.glyphmc.net`) — a separate world to
hang out and build. Claims and homes apply there. Your **$ and Glyphs** are
the same wallet. Inventory, chests, and bases are not. Each world has its
own `/ah`; items never cross. Bounties stay on anarchy. Active play pays
on both worlds: **$25 / 15 min** on anarchy, **$5 / 15 min** on Forever World.

| Address / command | Lands on |
| --- | --- |
| `anarchy.glyphmc.net` | Folia anarchy (`/ah`, `/bounty`, no claims) |
| `smp.glyphmc.net` | Forever World (claims, homes, own `/ah`, same `$` / Glyphs) |
| `play.glyphmc.net` | Anarchy (alias) |
| `/server anarchy` / `/server smp` | Switch while already online |
| https://glyphmc.net/map/ | Forever World live map (BlueMap) |

---

## First join

New players get:

- **$100** cash (one-time, kept even if the world is wiped)
- Stone sword, pickaxe, axe, shovel
- 16 bread and 16 torches
- A **rules book** (also readable anytime with `/rules`)

The book and tools are granted once per world playerdata — a map reset
re-issues them; starter cash does not.

| Command | What it does |
| --- | --- |
| `/rules` | Open the Glyph rules book |
| `/starter` | Claim the stone tools + book if you missed first join |

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
- **$25 per 15 minutes** of *active* play on anarchy, **$5** on Forever World (moving, mining, building — AFK does not pay)
- Selling on the auction house
- Collecting bounties by killing wanted players
- Getting paid by other players

Cash also shows on the **right-side HUD** and in the **Tab** player list.

---

## Forever World — homes and claims

Only on `smp.glyphmc.net` / `/server smp`. Anarchy has neither.

| Command | What it does |
| --- | --- |
| `/sethome [name]` | Set a home here (default name `home`). Up to 5 |
| `/home [name]` | Teleport to that home |
| `/delhome [name]` | Delete a home |
| `/homes` | List your homes |
| `/nickname [name]` | Character name in chat and tab. `/nickname off` clears |
| `/me <action>` | Nearby emote (`* Name waves`). Same 100-block range as local chat |
| Golden shovel | Claim land (right-click two corners) |
| Stick | Inspect whose claim you are standing in |
| `/trust <player>` | Let them build on your claim |
| `/containertrust <player>` | Chests only |
| `/accesstrust <player>` | Doors / buttons only |
| `/untrust <player>` | Remove trust |
| `/abandonclaim` | Drop the claim you are standing in |

Everyone starts with **10,000** claim blocks (about 100×100). Active play
earns **500** more per hour, up to **100,000**. Extra packs: **100 blocks
for $50** via `/claimblocks buy`. Land claims use the golden shovel and
`/claim`. Auction mail is `/ah mail`.

### Getting around and hanging out

| Command | What it does |
| --- | --- |
| `/spawn` | Teleport to world spawn |
| `/wild` | Random unclaimed overworld (5 min cooldown) |
| `/tpa <player>` | Ask to teleport to them |
| `/tpahere <player>` | Ask them to teleport to you |
| `/tpaccept` / `/tpdeny` | Answer a request (60s) |
| `/sit` | Sit. Right-click stairs/slabs too. Sneak to stand |
| `/warp <name>` | Go to a public player warp |
| `/warp set <name>` | List a warp here (**$250**, max 3, on land you can build) |
| `/warp delete <name>` | Remove your warp |
| `/warps` | List public warps |
| `/shop sell <price>` | Chest shop: others buy the held item from this chest |
| `/shop buy <price>` | Chest shop: others sell that item to this chest |
| `/shop remove` | Delete the shop you are looking at |
| `/trade <player>` | Secure item + money trade (within 10 blocks) |
| `/claimblocks` | Remaining claim blocks |
| `/claimblocks buy [packs]` | Buy 100 blocks for $50 per pack |
| `/mapimage <https url>` | Paint a held map from an image |

One player sleeping skips the night. Sneak + right-click an armor stand
on your land to pose it. Shop owners sneak + right-click to open the chest.

Staff: `/glyph setspawn` and `/glyph market build` (five stalls south of
spawn). CoreProtect (`/co`) is SMP-only. Live map:
**https://glyphmc.net/map/** (BlueMap, Forever World only).

---

## Auction house

Anarchy and the Forever World each have their own house. Same `$`, same fees.
Items listed on one world can only be bought and collected with `/ah mail` on that world.

| Command | What it does |
| --- | --- |
| `/ah` | Open the auction house GUI |
| `/ah sell <price>` | List the item in your hand |
| `/ah search <text>` | Search listings |
| `/ah mail` | Collect bought items / returned listings |

Aliases: `/auction`, `/auctionhouse`

**Fees (sinks):** ~1% listing fee when you post, ~5% sale fee from the seller’s proceeds. Listings expire after **48 hours**; unsold items come back through `/ah mail`.

---

## Bounties

| Command | What it does |
| --- | --- |
| `/bounty` or `/wanted` | Open the WANTED board (player heads, dead-or-alive) |
| `/bounty list` | Chat most-wanted list |
| `/bounty poster` | Print a written wanted list (item-frame it at spawn) |
| `/bounty poster <player>` | Print that player's wanted poster |
| `/bounty <player>` | Total bounty on a player |
| `/bounty add <player> <amount>` | Place a bounty (money escrowed) |

Minimum bounty: **$100**. Kill the target to claim the pot (anti-farm cooldowns apply).
New bounties and claims are announced server-wide.

---

## Stats

| Command | What it does |
| --- | --- |
| `/stats` | Your kills, deaths, blocks, playtime, etc. |
| `/stats <player>` | Someone else’s stats |
| `/playtime` | Your accumulated online time |
| `/top <category>` | Leaderboards: `money`, `kills`, `deaths`, `playtime`, `bounty` |

Tab list also shows each online player’s **money**, **Glyphs** (✦), and **death count**.

---

## Glyphs (prestige currency)

**✦ Glyphs** are account-bound prestige — permanent cosmetics only, never combat
power or `$` conversion. Not tradable; no `/glyphpay`.

| Command | What it does |
| --- | --- |
| `/glyphs` | Balance, lifetime earned, Discord tier, equipped flair |
| `/glyphs shop` | Catalog (name colors, titles, death messages) |
| `/glyphs buy <id>` | Purchase a product |
| `/glyphs color <id\|none>` | Equip an unlocked name color (white is free) |
| `/glyphs title <id\|none>` | Equip an unlocked title (shop or achievement) |
| `/glyphs death <id\|none>` | Equip a custom death message style |
| `/glyphs unlocks` | What you own |
| `/glyphs hud on\|off` | Show or hide the ✦ line on the cash sidebar |

**How you earn ✦**

- **3 ✦** on your first bounty claim (one-time)
- **Unique player kills** — milestones at 10 / 25 / 50 / 100 unique victims
- **Bounty claims** — milestones at 10 and 25 claims; title at 25
- **$1M lifetime AH sales** — unlocks Broker title
- Staff `/glyphadmin` (ops)

**Discord tiers** (Initiate → Legend) follow lifetime Glyphs earned — shown in
`/glyphs` and synced to Discord roles when linked. Unlocked titles sync as
their own Discord roles.

Tab list: `[Title] Name  $12.4K  ✦13  ☠29`. Sidebar HUD is opt-in via
`/glyphs hud on` (cash sidebar is always on).

---

## Discord link

| Command | What it does |
| --- | --- |
| `/linkdiscord` | Get a 10-minute code to bind Discord |
| `/unlinkdiscord` | Remove the Discord link |

In Discord: `/link GLYPH-XXXXXX` (ephemeral). Details: `docs/DISCORD.md`.
Codes and the `/link …` line are **click-to-copy**.

---

## Chat

Normal chat is **local** — players within **100 blocks** (same world). Prefix `[Local]`.

| Feature | What it does |
| --- | --- |
| Just type | Nearby only |
| `/g <message>` or `/global` | Everyone on this world. Prefix `[Global]` |
| `/l <message>` | Nearby (same as default chat) |
| Type `[i]` or `[item]` in chat | Shows your held item — **hover** for the full tooltip, **click** to copy a short summary |
| `/item` | Announce your held item the same way (local) |

Example: `WTS [item] 5k` while holding an elytra.

---

## Voice chat & client mods

Voice is **proximity** (Simple Voice Chat). Not required to join.

Optional modpack: [glyph-clientmods](https://github.com/KyleLandon/glyph-clientmods) — voice, minimap/world map, performance, JEI, etc.

Tip: move Xaero’s minimap to the **left** (`Y` → Change Position) so it does not cover the cash HUD on the right.

---

## Quick start

1. Join `play.glyphmc.net` — you start with **$100**, stone tools, and a rules book (`/rules`).
2. Leave spawn, gather gear, make a hidden base (nothing protects it but distance and secrecy).
3. `/ah sell <price>` to list loot; `/ah` to browse and buy.
4. `/ah mail` when you have mail.
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
| `/glyphadmin get <player>` | Inspect Glyph balance |
| `/glyphadmin set <player> <amount>` | Set Glyphs |
| `/glyphadmin add <player> <amount>` | Add Glyphs |
| `/glyphadmin remove <player> <amount>` | Remove Glyphs |
| `/glyphadmin unlinkdiscord <player>` | Force-unlink Discord |
| `/starter <player>` | Force-give the starter pack (player must be online) |
| `/glyph status` | Infrastructure health |
| `/glyph version` | Plugin version |
| `/glyph setspawn` | Forever World: set world spawn here |
| `/glyph market build` | Forever World: build the 5-stall street south of spawn |
| `/restart` | 10-second chat countdown, then restart Folia |
| `/glyph restart` | Same as `/restart` |
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
 /ah  ·  /ah sell <price>  ·  /ah search <text>  ·  /ah mail

Bounties
 /bounty  ·  /wanted  ·  /bounty poster  ·  /bounty add <player> <amount>  (min $100)

Stats
 /stats  ·  /stats <player>  ·  /playtime  ·  /top <money|kills|deaths|playtime|bounty>
 /rules  ·  /starter

Discord link
 /linkdiscord  ·  then /link <code> in Discord

Forever World (smp.glyphmc.net)
 /spawn  /wild  /tpa  /home  /warp  /shop  /trade  /sit  /claimblocks
 Claims, homes, chest shops. Same $ / Glyphs. Own /ah.

Voice: Simple Voice Chat (optional) — pack: github.com/KyleLandon/glyph-clientmods

Discord: https://discord.gg/htkQHR4gdf
Site: https://glyphmc.net
Map:  https://glyphmc.net/map
```
