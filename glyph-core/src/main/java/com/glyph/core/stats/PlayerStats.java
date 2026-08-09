package com.glyph.core.stats;

import java.util.UUID;

/** One {@code player_stats} row (GDD section 56). */
public record PlayerStats(
        UUID playerUuid,
        long kills,
        long deaths,
        long mobKills,
        long blocksBroken,
        long blocksPlaced,
        long distanceCm,
        long auctionSales,
        long auctionPurchases,
        long bountiesClaimed) {

    public static PlayerStats empty(UUID playerUuid) {
        return new PlayerStats(playerUuid, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Kill/death ratio; deaths of zero count as one to stay defined. */
    public double killDeathRatio() {
        return kills / (double) Math.max(1, deaths);
    }
}
