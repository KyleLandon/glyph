package com.glyph.api.economy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The economy service (GDD sections 14-20, 134).
 *
 * <p>All operations are asynchronous and never block a tick thread. Balance
 * mutations are atomic PostgreSQL transactions with row locks; every change
 * writes a ledger entry. Amounts are {@link Money} — BIGINT whole dollars,
 * never floating point.</p>
 */
public interface EconomyApi {

    /** @return the player's balance, empty if they have no account */
    CompletableFuture<Optional<Money>> balance(UUID playerUuid);

    /**
     * Transfers money between two player accounts atomically.
     *
     * @param source         paying player
     * @param destination    receiving player
     * @param amount         must be positive
     * @param idempotencyKey optional (nullable) unique key; retrying with the
     *                       same key returns {@code DUPLICATE_REQUEST} instead
     *                       of transferring twice
     */
    CompletableFuture<TransferResult> transfer(
            UUID source, UUID destination, Money amount, String idempotencyKey);

    /** Top balances for {@code /baltop}, largest first. */
    CompletableFuture<List<TopBalance>> topBalances(int limit);

    /** Most recent ledger entries involving the player, newest first. */
    CompletableFuture<List<LedgerEntry>> history(UUID playerUuid, int limit);

    /**
     * Administrative balance mutation ({@code /eco set|add|remove}). Mints or
     * burns currency; always ledgered as {@code ADMIN_ADJUSTMENT} with the
     * acting admin recorded (GDD section 18).
     *
     * @param actor admin UUID, or null for the console
     */
    CompletableFuture<TransferResult> adminAdjust(
            UUID playerUuid, AdminOperation operation, Money amount, UUID actor);

    enum AdminOperation { SET, ADD, REMOVE }
}
