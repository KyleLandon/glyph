package com.glyph.api.discord;

import java.util.List;
import java.util.Optional;

/**
 * Lifetime-Glyph Discord prestige tiers (ADR-011 / ADR-012).
 *
 * <p>Tiers derive from {@code glyphs_lifetime_earned}, never current balance.</p>
 */
public enum DiscordTier {
    INITIATE(10, "Initiate"),
    SCOUT(25, "Scout"),
    BLOODED(50, "Blooded"),
    VETERAN(100, "Veteran"),
    LEGEND(250, "Legend");

    private final long minLifetimeEarned;
    private final String displayName;

    DiscordTier(long minLifetimeEarned, String displayName) {
        this.minLifetimeEarned = minLifetimeEarned;
        this.displayName = displayName;
    }

    public long minLifetimeEarned() {
        return minLifetimeEarned;
    }

    public String displayName() {
        return displayName;
    }

    /** Highest tier the player has earned, if any. */
    public static Optional<DiscordTier> forLifetimeEarned(long lifetimeEarned) {
        DiscordTier best = null;
        for (DiscordTier tier : values()) {
            if (lifetimeEarned >= tier.minLifetimeEarned) {
                best = tier;
            }
        }
        return Optional.ofNullable(best);
    }

    /** All tiers at or below the earned tier (for role cleanup). */
    public static List<DiscordTier> all() {
        return List.of(values());
    }
}
