package com.glyph.core.config;

/**
 * Auction house configuration (GDD sections 21, 63). Fee percentages are
 * stored as basis points so fee math stays in integer minor units.
 *
 * @param enabled              master switch for /ah
 * @param listingFeeBasisPoints fee charged at listing time (100 = 1%)
 * @param saleFeeBasisPoints    fee deducted from seller proceeds (500 = 5%)
 * @param maxListingsPerPlayer  concurrent ACTIVE listings per seller
 * @param durationHours         listing lifetime before expiry
 */
public record AuctionSettings(
        boolean enabled,
        int listingFeeBasisPoints,
        int saleFeeBasisPoints,
        int maxListingsPerPlayer,
        int durationHours) {

    /** Percent-of-price in integer math, rounding up so fees are never free. */
    public static long fee(long priceMinor, int basisPoints) {
        if (basisPoints <= 0 || priceMinor <= 0) {
            return 0;
        }
        return Math.ceilDiv(Math.multiplyExact(priceMinor, basisPoints), 10_000L);
    }

    public long listingFee(long priceMinor) {
        return fee(priceMinor, listingFeeBasisPoints);
    }

    public long saleFee(long priceMinor) {
        return fee(priceMinor, saleFeeBasisPoints);
    }
}
