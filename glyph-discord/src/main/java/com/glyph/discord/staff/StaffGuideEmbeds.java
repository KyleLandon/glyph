package com.glyph.discord.staff;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Embed content for the Glyph staff guide (from docs/PLAYER_COMMANDS.md). */
public final class StaffGuideEmbeds {

    private static final Color GREEN = new Color(0x43A047);
    private static final Color AMBER = new Color(0xF9A825);
    private static final Color RED = new Color(0xE53935);
    private static final Color TEAL = new Color(0x26C6DA);
    private static final Color GRAY = new Color(0x78909C);
    private static final Color PURPLE = new Color(0x8E24AA);

    private static final Map<StaffGuideTopic, MessageEmbed> CACHE = buildAll();

    private StaffGuideEmbeds() {
    }

    public static MessageEmbed embed(StaffGuideTopic topic) {
        return CACHE.get(topic);
    }

    public static MessageEmbed indexEmbed() {
        return new EmbedBuilder()
                .setTitle("Glyph Staff Guide")
                .setColor(TEAL)
                .setDescription("""
                        Permanent reference lives in **#staff-guide** (forum).
                        For a quick lookup, run `/staffhelp` and pick a topic \
                        (ephemeral — only you see it).

                        **Quick ops**
                        `/glyph status` · `/eco get <player>` · `/stats <player>` · `/tps`
                        """)
                .addField("📌 START HERE", "Server Overview", true)
                .addField("💵 ECONOMY", "Money · Auction · Bounties", true)
                .addField("✦ PRESTIGE", "Glyphs · Discord roles", true)
                .addField("🛠 STAFF", "Commands · Policy · Troubleshooting", true)
                .addField("🔗 SYSTEMS", "Discord linking · Client mods", true)
                .setFooter("play.glyphmc.net · glyphmc.net")
                .setTimestamp(Instant.now())
                .build();
    }

    private static Map<StaffGuideTopic, MessageEmbed> buildAll() {
        EnumMap<StaffGuideTopic, MessageEmbed> map = new EnumMap<>(StaffGuideTopic.class);
        map.put(StaffGuideTopic.OVERVIEW, overview());
        map.put(StaffGuideTopic.ECONOMY, economy());
        map.put(StaffGuideTopic.AUCTION, auction());
        map.put(StaffGuideTopic.BOUNTIES, bounties());
        map.put(StaffGuideTopic.GLYPHS, glyphs());
        map.put(StaffGuideTopic.DISCORD, discord());
        map.put(StaffGuideTopic.STAFF_COMMANDS, staffCommands());
        map.put(StaffGuideTopic.POLICY, policy());
        map.put(StaffGuideTopic.TROUBLESHOOTING, troubleshooting());
        map.put(StaffGuideTopic.MODS, mods());
        return Map.copyOf(map);
    }

    private static MessageEmbed overview() {
        return base(StaffGuideTopic.OVERVIEW, GRAY)
                .setDescription("Persistent **anarchy + economy**. Amounts are whole dollars (no cents).")
                .addField("Rules of the road", """
                        • **No** land claims / region protection
                        • **No** staff item recovery
                        • **No** free TPs / homes / warps (beyond spawn)
                        • Spawn = small protected zone (~96 blocks)
                        • Voice = proximity (optional Simple Voice Chat)
                        """, false)
                .addField("Addresses", """
                        Minecraft: `play.glyphmc.net`
                        Site: https://glyphmc.net
                        Docs: `docs/PLAYER_COMMANDS.md`
                        """, false)
                .build();
    }

    private static MessageEmbed economy() {
        return base(StaffGuideTopic.ECONOMY, GREEN)
                .addField("Commands", """
                        `/bal` `/balance [player]` — cash
                        `/pay <player> <amount>` — transfer
                        `/baltop` — richest
                        `/money history` — ledger
                        """, false)
                .addField("Income", """
                        • **$100** first join (one-time)
                        • **~$10 / 15m** *active* play (AFK = $0)
                        • Auction sales · bounties · `/pay` from others
                        """, false)
                .addField("Display", "Tab list + optional sidebar (`/glyphs hud on`)", false)
                .build();
    }

    private static MessageEmbed auction() {
        return base(StaffGuideTopic.AUCTION, AMBER)
                .addField("Commands", """
                        `/ah` — GUI
                        `/ah sell <price>` — list held item
                        `/ah search <text>` — search
                        `/ah mail` — deliveries / returns
                        """, false)
                .addField("Fees & expiry", """
                        ~**1%** listing fee · ~**5%** sale fee (sinks)
                        Listings last **48h**; unsold returns via `/ah mail`
                        """, false)
                .build();
    }

    private static MessageEmbed bounties() {
        return base(StaffGuideTopic.BOUNTIES, RED)
                .addField("Commands", """
                        `/bounty` — most-wanted board
                        `/bounty <player>` — total on a player
                        `/bounty add <player> <amount>` — place (escrowed)
                        """, false)
                .addField("Rules", """
                        Minimum **$100**
                        Kill target to claim pot
                        Anti-farm cooldowns on same victim
                        """, false)
                .build();
    }

    private static MessageEmbed glyphs() {
        return base(StaffGuideTopic.GLYPHS, PURPLE)
                .setDescription("""
                        **✦ Glyphs** = account-bound prestige. Cosmetics only.
                        Not tradable. No `/glyphpay`. Never converts to `$`.
                        """)
                .addField("Commands", """
                        `/glyphs` · `shop` · `buy <id>`
                        `color` · `title` · `death` · `unlocks`
                        `hud on|off`
                        """, false)
                .addField("Earn ✦ (milestones)", """
                        • First bounty claim → **3 ✦**
                        • Unique kills @ 10 / 25 / 50 / 100
                        • Bounty claims @ 10 / 25 (title @ 25)
                        • $1M lifetime AH sales → Broker title
                        • Staff `/glyphadmin`
                        """, false)
                .addField("Discord sync", """
                        Lifetime ✦ earned → Initiate / Scout / Blooded / Veteran / Legend
                        Unlocked titles → Bounty Hunter / Broker / Blooded / shop titles
                        Synced when Discord is linked. Spending ✦ never demotes.
                        """, false)
                .build();
    }

    private static MessageEmbed discord() {
        return base(StaffGuideTopic.DISCORD, TEAL)
                .addField("Player flow", """
                        1. In-game `/linkdiscord` → code (click to copy)
                        2. Discord **slash** `/link` → paste `GLYPH-XXXXXX`
                        3. Gets **Verified** + prestige role if earned
                        """, false)
                .addField("Notes", """
                        Codes expire in **10 minutes**
                        Must use the **slash command**, not a normal chat message
                        Unlink: `/unlinkdiscord`
                        Ops: `/glyphadmin unlinkdiscord <player>`
                        Alpha access: Discord role **Glyph Alpha** or `/alpha grant`
                        """, false)
                .build();
    }

    private static MessageEmbed staffCommands() {
        return base(StaffGuideTopic.STAFF_COMMANDS, AMBER)
                .addField("Money", """
                        `/eco get|set|add|remove <player> [amount]`
                        """, false)
                .addField("Glyphs", """
                        `/glyphadmin get|set|add|remove <player> [amount]`
                        `/glyphadmin unlinkdiscord <player>`
                        """, false)
                .addField("Infra", """
                        `/glyph status` — DB / Redis health
                        `/glyph version`
                        `/tps` — Folia region tick health
                        """, false)
                .addField("Discord bot", """
                        `/staffhelp` — this menu
                        `/staffguide setup` — publish #staff-guide forum
                        `/alpha grant|revoke @user`
                        """, false)
                .build();
    }

    private static MessageEmbed policy() {
        return base(StaffGuideTopic.POLICY, RED)
                .addField("Do", """
                        • Investigate with `/eco get`, `/stats`, `/glyph status`
                        • Keep `$` and ✦ ledgers separate in your head
                        • Point players at `/linkdiscord` + slash `/link`
                        • Prefer teaching the anarchy contract over soft-rolling it
                        """, false)
                .addField("Don't", """
                        • Replace lost items / undo deaths
                        • Free-TP players out of trouble
                        • Refund `$` losses with ✦ (or the reverse)
                        • Hand-wave economy disputes without ledger checks
                        """, false)
                .build();
    }

    private static MessageEmbed troubleshooting() {
        return base(StaffGuideTopic.TROUBLESHOOTING, GRAY)
                .addField("Checks", """
                        `/glyph status` — Postgres / Redis
                        `/tps` — lag / region health
                        Player broke? `/eco get` + `/stats` + `/glyphs`
                        Discord link fail? New `/linkdiscord` code (10m TTL)
                        """, false)
                .addField("Discord bot", """
                        Slash `/link` must autocomplete from the Glyph bot
                        Forum missing? Run `/staffguide setup`
                        Whitelist (when enabled): needs **Glyph Alpha** + linked account
                        """, false)
                .build();
    }

    private static MessageEmbed mods() {
        return base(StaffGuideTopic.MODS, TEAL)
                .addField("Voice", "Proximity Simple Voice Chat — optional, not required to join.", false)
                .addField("Modpack", "https://github.com/KyleLandon/glyph-clientmods", false)
                .addField("HUD tip", "Move Xaero minimap **left** (`Y` → Change Position) so it doesn't cover the $ HUD.", false)
                .build();
    }

    private static EmbedBuilder base(StaffGuideTopic topic, Color color) {
        return new EmbedBuilder()
                .setTitle(topic.emoji() + " " + topic.title())
                .setColor(color)
                .setFooter("Glyph Staff Guide · /staffhelp")
                .setTimestamp(Instant.now());
    }
}
