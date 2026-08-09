package com.glyph.api.economy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One row of the transaction ledger as seen by a player or admin
 * ({@code /money history}).
 *
 * @param id          transaction UUID
 * @param sourceOwner player who paid, empty for mints (money entering circulation)
 * @param destOwner   player who received, empty for burns (money leaving circulation)
 * @param amount      always positive
 * @param type        ledger category
 * @param reason      human-readable context, may be empty
 * @param createdAt   database timestamp
 */
public record LedgerEntry(
        UUID id,
        Optional<UUID> sourceOwner,
        Optional<UUID> destOwner,
        Money amount,
        TransactionType type,
        String reason,
        Instant createdAt) {
}
