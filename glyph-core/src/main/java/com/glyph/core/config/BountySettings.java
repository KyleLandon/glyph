package com.glyph.core.config;

/**
 * Bounty configuration (GDD sections 25, 63).
 *
 * @param enabled                    master switch for /bounty
 * @param minimumMinor               smallest bounty, in minor units
 * @param sameVictimCooldownMinutes  killing the same victim again within this
 *                                   window records the kill but withholds the
 *                                   payout (anti-farming)
 */
public record BountySettings(
        boolean enabled,
        long minimumMinor,
        int sameVictimCooldownMinutes) {
}
