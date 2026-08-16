package com.glyph.core.economy;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TopBalance;
import com.glyph.api.economy.TransactionType;
import com.glyph.api.economy.TransferResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Canonical {@link EconomyApi} implementation (GDD sections 45, 134).
 *
 * <p>Validation happens here; atomicity happens in the repository; every call
 * runs on the async executor. When a balance changes, registered listeners
 * (the money HUD) are notified with the new balance — no polling.</p>
 */
public final class EconomyService implements EconomyApi {

    private final EconomyRepository repository;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    /** Notified with (playerUuid, newBalance) after any committed change. */
    private final List<BiConsumer<UUID, Money>> balanceListeners = new CopyOnWriteArrayList<>();

    public EconomyService(
            EconomyRepository repository,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void addBalanceListener(BiConsumer<UUID, Money> listener) {
        balanceListeners.add(listener);
    }

    /**
     * Publishes a balance change made outside this service (the Vault bridge
     * writes through the repository directly) so HUDs and other listeners
     * stay in sync.
     */
    public void publishBalanceChange(UUID playerUuid, Money newBalance) {
        notifyBalance(playerUuid, newBalance.dollars());
    }

    /**
     * Reloads the player's balance from the database and notifies listeners.
     * Used after join persistence: the starting-balance mint bypasses this
     * service, and a HUD fetch that races ahead of account creation would
     * otherwise stick on empty/`$ 0`.
     */
    public CompletableFuture<Void> resyncBalance(UUID playerUuid) {
        return balance(playerUuid)
                .thenAccept(balance -> balance.ifPresent(
                        money -> notifyBalance(playerUuid, money.dollars())))
                .exceptionally(error -> {
                    logger.debug("Balance resync failed for {}", playerUuid, error);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Optional<Money>> balance(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Economy unavailable: database is down"));
        }
        return CompletableFuture.supplyAsync(
                () -> repository.balance(playerUuid).map(Money::of), ioExecutor);
    }

    @Override
    public CompletableFuture<TransferResult> transfer(
            UUID source, UUID destination, Money amount, String idempotencyKey) {
        if (amount == null || !amount.isPositive()) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.INVALID_AMOUNT));
        }
        // Self-payment policy (GDD section 134): rejected. Paying yourself
        // would only inflate lifetime statistics.
        if (source.equals(destination)) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.SELF_PAYMENT));
        }
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.FAILED));
        }
        return CompletableFuture
                .supplyAsync(() -> repository.transfer(
                        source, destination, amount.dollars(), idempotencyKey), ioExecutor)
                .thenApply(outcome -> {
                    if (outcome.result().isSuccess()) {
                        logger.info("Transfer {}: {} -> {} ({})",
                                outcome.result().transactionId().orElse(null),
                                source, destination, amount);
                        notifyBalance(source, outcome.sourceBalanceAfter());
                        notifyBalance(destination, outcome.destBalanceAfter());
                    }
                    return outcome.result();
                })
                .exceptionally(error -> {
                    logger.error("Transfer failed: {} -> {} ({})", source, destination, amount, error);
                    return TransferResult.failure(TransferResult.Status.FAILED);
                });
    }

    @Override
    public CompletableFuture<List<TopBalance>> topBalances(int limit) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Economy unavailable: database is down"));
        }
        int capped = Math.clamp(limit, 1, 25);
        return CompletableFuture.supplyAsync(() -> repository.topBalances(capped), ioExecutor);
    }

    @Override
    public CompletableFuture<List<LedgerEntry>> history(UUID playerUuid, int limit) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Economy unavailable: database is down"));
        }
        int capped = Math.clamp(limit, 1, 50);
        return CompletableFuture.supplyAsync(
                () -> repository.history(playerUuid, capped), ioExecutor);
    }

    /**
     * Mint ({@link AdminOperation#ADD}) or burn ({@link AdminOperation#REMOVE})
     * through the ledger as {@code SYSTEM_REWARD} / {@code SYSTEM_SINK}.
     * Used by Forever World warps, claim-block packs, and similar sinks.
     */
    public CompletableFuture<TransferResult> systemAdjust(
            UUID playerUuid, AdminOperation operation, Money amount,
            TransactionType type, String reason) {
        if (amount == null || !amount.isPositive()) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.INVALID_AMOUNT));
        }
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.FAILED));
        }
        return CompletableFuture
                .supplyAsync(() -> repository.externalAdjust(
                        playerUuid, operation, amount.dollars(), type, reason), ioExecutor)
                .thenApply(outcome -> {
                    if (outcome.result().isSuccess()) {
                        notifyBalance(playerUuid, outcome.sourceBalanceAfter());
                    }
                    return outcome.result();
                })
                .exceptionally(error -> {
                    logger.error("System adjust failed: {} {} on {} ({})",
                            operation, amount, playerUuid, reason, error);
                    return TransferResult.failure(TransferResult.Status.FAILED);
                });
    }

    @Override
    public CompletableFuture<TransferResult> adminAdjust(
            UUID playerUuid, AdminOperation operation, Money amount, UUID actor) {
        if (amount == null) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.INVALID_AMOUNT));
        }
        // SET 0 is legal; ADD/REMOVE of zero is a no-op worth rejecting.
        if (operation != AdminOperation.SET && !amount.isPositive()) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.INVALID_AMOUNT));
        }
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(
                    TransferResult.failure(TransferResult.Status.FAILED));
        }
        return CompletableFuture
                .supplyAsync(() -> repository.adminAdjust(
                        playerUuid, operation, amount.dollars(), actor), ioExecutor)
                .thenApply(outcome -> {
                    if (outcome.result().isSuccess()) {
                        // GDD section 18: every administrative change MUST be logged.
                        logger.info("Admin economy adjustment: {} {} on {} by {} (new balance {})",
                                operation, amount, playerUuid,
                                actor == null ? "console" : actor,
                                outcome.result().newBalance().orElse(Money.ZERO));
                        notifyBalance(playerUuid, outcome.sourceBalanceAfter());
                    }
                    return outcome.result();
                })
                .exceptionally(error -> {
                    logger.error("Admin adjustment failed: {} {} on {}",
                            operation, amount, playerUuid, error);
                    return TransferResult.failure(TransferResult.Status.FAILED);
                });
    }

    private void notifyBalance(UUID playerUuid, long balance) {
        if (balance < 0) {
            return;
        }
        Money money = Money.of(balance);
        for (BiConsumer<UUID, Money> listener : balanceListeners) {
            try {
                listener.accept(playerUuid, money);
            } catch (Exception e) {
                logger.error("Balance listener failed for {}", playerUuid, e);
            }
        }
    }
}
