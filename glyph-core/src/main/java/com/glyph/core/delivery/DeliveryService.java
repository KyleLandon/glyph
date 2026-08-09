package com.glyph.core.delivery;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Delivery queue orchestration (GDD section 23). The claim protocol is
 * two-phase: rows are marked CLAIMED in the database first, then the items
 * are handed over on the recipient's entity thread; if the handover cannot
 * happen (player gone), {@link #revert} puts them back to PENDING.
 */
public final class DeliveryService {

    private final DeliveryRepository repository;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    public DeliveryService(
            DeliveryRepository repository,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Recovery path: queue an item for a player who is no longer online. */
    public void createReturn(UUID recipient, byte[] payload, String reason) {
        CompletableFuture.runAsync(() -> repository.create(
                        recipient, "ITEM_RETURN", payload,
                        "{\"source\":\"" + reason + "\"}"), ioExecutor)
                .exceptionally(error -> {
                    logger.error("Failed to queue return delivery for {} ({})",
                            recipient, reason, error);
                    return null;
                });
    }

    public CompletableFuture<List<Delivery>> claim(UUID recipient, int limit) {
        if (!databaseReady.getAsBoolean() || limit <= 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture
                .supplyAsync(() -> repository.claim(recipient, limit), ioExecutor)
                .exceptionally(error -> {
                    logger.error("Delivery claim failed for {}", recipient, error);
                    return List.of();
                });
    }

    public void revert(List<UUID> deliveryIds) {
        if (deliveryIds.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> repository.revert(deliveryIds), ioExecutor)
                .exceptionally(error -> {
                    logger.error("Delivery revert failed for {} item(s): {}",
                            deliveryIds.size(), deliveryIds, error);
                    return null;
                });
    }

    public CompletableFuture<Integer> pendingCount(UUID recipient) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture
                .supplyAsync(() -> repository.pendingCount(recipient), ioExecutor)
                .exceptionally(error -> {
                    logger.error("Delivery count failed for {}", recipient, error);
                    return 0;
                });
    }
}
