package com.glyph.core.stats;

/**
 * Buffered player statistic counters (GDD sections 30, 56). Each value maps
 * to one {@code player_stats} column.
 */
public enum StatType {
    KILLS("kills"),
    DEATHS("deaths"),
    MOB_KILLS("mob_kills"),
    BLOCKS_BROKEN("blocks_broken"),
    BLOCKS_PLACED("blocks_placed"),
    DISTANCE_CM("distance_cm"),
    AUCTION_SALES("auction_sales"),
    AUCTION_PURCHASES("auction_purchases"),
    BOUNTIES_CLAIMED("bounties_claimed");

    private final String column;

    StatType(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
