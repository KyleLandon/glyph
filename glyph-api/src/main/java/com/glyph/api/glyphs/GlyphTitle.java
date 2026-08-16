package com.glyph.api.glyphs;

import java.util.Locale;
import java.util.Optional;

/**
 * In-game titles that also map to Discord roles (shop + achievement unlocks).
 */
public enum GlyphTitle {
    WANDERER("title_wanderer", "Wanderer"),
    OUTLAW("title_outlaw", "Outlaw"),
    WARLORD("title_warlord", "Warlord"),
    BLOODED("title_blooded", "Blooded"),
    HUNTER("title_hunter", "Bounty Hunter"),
    BROKER("title_broker", "Broker");

    private final String id;
    private final String displayName;

    GlyphTitle(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<GlyphTitle> fromId(String unlockId) {
        if (unlockId == null || unlockId.isBlank()) {
            return Optional.empty();
        }
        String normalized = unlockId.trim().toLowerCase(Locale.ROOT);
        for (GlyphTitle title : values()) {
            if (title.id.equals(normalized)) {
                return Optional.of(title);
            }
        }
        return Optional.empty();
    }
}
