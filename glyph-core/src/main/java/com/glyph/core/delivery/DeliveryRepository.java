package com.glyph.core.delivery;

import java.util.List;
import java.util.UUID;

/**
 * Persistence contract for the delivery queue (GDD section 23).
 *
 * <p>Deliveries are <em>created</em> inside auction transactions (see
 * {@code PostgresAuctionRepository}); this repository only claims and counts
 * them.</p>
 */
public interface DeliveryRepository {

    /**
     * Inserts a standalone PENDING delivery. Auction flows create deliveries
     * inside their own transactions; this exists for recovery paths (e.g. a
     * failed listing whose seller logged off before the item could be
     * handed back).
     */
    void create(UUID recipientUuid, String type, byte[] payload, String metadataJson);

    /**
     * Atomically marks up to {@code limit} PENDING deliveries CLAIMED and
     * returns them, oldest first. Rows are locked with {@code SKIP LOCKED}
     * so concurrent claims never hand out the same delivery twice.
     */
    List<Delivery> claim(UUID recipientUuid, int limit);

    /**
     * Returns claimed deliveries to PENDING — used when the recipient
     * disconnects between the claim commit and the in-game item handover.
     */
    void revert(List<UUID> deliveryIds);

    int pendingCount(UUID recipientUuid);
}
