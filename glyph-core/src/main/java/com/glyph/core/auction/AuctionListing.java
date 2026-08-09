package com.glyph.core.auction;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One auction house listing (GDD section 52). {@code itemData} is the
 * immutable serialized item snapshot; {@code summaryJson} is the denormalized
 * browse copy (see {@link com.glyph.core.item.ItemCodec}).
 */
public record AuctionListing(
        UUID id,
        UUID sellerUuid,
        byte[] itemData,
        String summaryJson,
        long priceMinor,
        long listingFeeMinor,
        Status status,
        Optional<UUID> buyerUuid,
        Instant createdAt,
        Instant expiresAt,
        Optional<Instant> soldAt) {

    public enum Status { ACTIVE, SOLD, CANCELLED, EXPIRED }
}
