package com.glyph.core.config;

/**
 * Forever World-only knobs (ADR-013). Anarchy ignores this object.
 */
public record SmpSettings(
        int wildMinRadius,
        int wildMaxRadius,
        int wildCooldownSeconds,
        int wildMaxAttempts,
        int tpaTimeoutSeconds,
        boolean onePlayerSleep,
        long warpCreateCost,
        int maxWarpsPerPlayer,
        long claimBlockPackPrice,
        int claimBlockPackSize,
        boolean sitEnabled,
        boolean shopsEnabled,
        boolean tradeEnabled,
        boolean imageMapsEnabled) {

    public static SmpSettings defaults() {
        return new SmpSettings(
                500, 15_000, 300, 24,
                60,
                true,
                250, 3,
                50, 100,
                true, true, true, true);
    }
}
