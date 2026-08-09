package com.glyph.core.delivery;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the persistent delivery queue (GDD sections 23, 53).
 * {@code payload} is a serialized item (see
 * {@link com.glyph.core.item.ItemCodec}).
 */
public record Delivery(
        UUID id,
        UUID recipientUuid,
        String type,
        byte[] payload,
        String metadataJson,
        Instant createdAt) {
}
