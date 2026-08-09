package com.glyph.core.config;

/**
 * Economy configuration (GDD section 55). All amounts are whole dollars —
 * the economy has no cents.
 *
 * @param startingBalance balance granted on account creation, in dollars
 * @param currencySymbol  prefix for formatted amounts, e.g. {@code $}
 * @param hudEnabled      whether the sidebar money HUD is shown
 * @param hudTitle        title line of the sidebar HUD
 */
public record EconomySettings(
        long startingBalance,
        String currencySymbol,
        boolean hudEnabled,
        String hudTitle) {
}
