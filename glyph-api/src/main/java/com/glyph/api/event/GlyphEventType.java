package com.glyph.api.event;

import java.util.Locale;
import java.util.Optional;

public enum GlyphEventType {
    GLYPH_LIFETIME("glyph.lifetime"),
    GLYPH_TITLE("glyph.title"),
    DISCORD_LINKED("discord.linked");

    private final String wireName;

    GlyphEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<GlyphEventType> fromWire(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            return Optional.empty();
        }
        String normalized = wireName.trim().toLowerCase(Locale.ROOT);
        for (GlyphEventType type : values()) {
            if (type.wireName.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
