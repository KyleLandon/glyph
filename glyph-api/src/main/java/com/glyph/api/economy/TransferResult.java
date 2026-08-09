package com.glyph.api.economy;

import java.util.Optional;
import java.util.UUID;

/**
 * Outcome of a transfer or administrative adjustment. Failures are expected
 * business results (insufficient funds, unknown account), not exceptions.
 *
 * @param status        what happened
 * @param transactionId ledger entry id when a balance changed
 * @param newBalance    the acting player's balance after the operation
 */
public record TransferResult(
        Status status,
        Optional<UUID> transactionId,
        Optional<Money> newBalance) {

    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        ACCOUNT_NOT_FOUND,
        INVALID_AMOUNT,
        SELF_PAYMENT,
        DUPLICATE_REQUEST,
        /** Infrastructure failure (database down, timeout); already logged. */
        FAILED
    }

    public static TransferResult success(UUID transactionId, Money newBalance) {
        return new TransferResult(Status.SUCCESS,
                Optional.of(transactionId), Optional.of(newBalance));
    }

    public static TransferResult failure(Status status) {
        return new TransferResult(status, Optional.empty(), Optional.empty());
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
