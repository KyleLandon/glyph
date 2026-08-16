package com.glyph.discord.staff;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Topics for /staffhelp and #staff-guide forum posts. */
public enum StaffGuideTopic {
    OVERVIEW("overview", "Server Overview", "📌", "What Glyph is — anarchy + economy rules"),
    ECONOMY("economy", "Money", "💵", "Balances, pay, baltop"),
    AUCTION("auction", "Auction House", "📦", "AH, fees, /ah mail"),
    BOUNTIES("bounties", "Bounties", "☠", "Wanted board and payouts"),
    GLYPHS("glyphs", "Glyph Prestige", "✦", "Account-bound ✦ cosmetics"),
    DISCORD("discord", "Discord Linking", "🔗", "Link Minecraft ↔ Discord"),
    STAFF_COMMANDS("staff", "Staff Commands", "🛠", "Ops-only commands"),
    POLICY("policy", "Staff Policy", "📋", "What staff should / shouldn't do"),
    TROUBLESHOOTING("troubleshoot", "Troubleshooting", "🔧", "Common ops checks"),
    MODS("mods", "Client Mods", "🎙", "Voice chat + client modpack");

    private final String id;
    private final String title;
    private final String emoji;
    private final String description;

    StaffGuideTopic(String id, String title, String emoji, String description) {
        this.id = id;
        this.title = title;
        this.emoji = emoji;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String emoji() {
        return emoji;
    }

    public String description() {
        return description;
    }

    public String forumPostName() {
        return emoji + " " + title;
    }

    public static Optional<StaffGuideTopic> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.id.equals(needle)).findFirst();
    }
}
