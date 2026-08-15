package com.glyph.core.config;

/**
 * Prestige currency (Glyphs) configuration — see {@code docs/GLYPHS.md}.
 */
public record GlyphCurrencySettings(
        boolean enabled,
        String symbol,
        long firstBountyReward) {

    public GlyphCurrencySettings {
        if (symbol == null || symbol.isBlank()) {
            symbol = "✦";
        }
        firstBountyReward = Math.max(0L, firstBountyReward);
    }
}
