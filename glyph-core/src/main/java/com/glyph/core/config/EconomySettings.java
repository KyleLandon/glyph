package com.glyph.core.config;

/**
 * Economy configuration (GDD section 55).
 *
 * @param startingBalanceMinor balance granted on account creation, in minor
 *                             units (cents); anarchy default is 0
 * @param currencySymbol       prefix for formatted amounts, e.g. {@code $}
 * @param hudEnabled           whether the sidebar money HUD is shown
 * @param hudTitle             title line of the sidebar HUD
 */
public record EconomySettings(
        long startingBalanceMinor,
        String currencySymbol,
        boolean hudEnabled,
        String hudTitle) {
}
