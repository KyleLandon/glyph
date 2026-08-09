package com.glyph.core.bounty;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** One bounty (GDD sections 25, 54). Amount is escrowed while ACTIVE. */
public record Bounty(
        UUID id,
        UUID targetUuid,
        UUID creatorUuid,
        long amountMinor,
        Status status,
        Optional<UUID> claimedBy,
        Instant createdAt,
        Optional<Instant> claimedAt) {

    public enum Status { ACTIVE, CLAIMED }
}
