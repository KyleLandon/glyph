package com.glyph.core.config;

import java.util.Locale;

/**
 * Which backend this GlyphCore instance is. Same Postgres wallet; different
 * rules. See ADR-013.
 */
public enum ServerRole {
    ANARCHY,
    SMP;

    public boolean isSmp() {
        return this == SMP;
    }

    /** Auction / delivery partition. Items never cross this key. */
    public String marketId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ServerRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANARCHY;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("smp") || normalized.equals("survival")) {
            return SMP;
        }
        return ANARCHY;
    }
}
