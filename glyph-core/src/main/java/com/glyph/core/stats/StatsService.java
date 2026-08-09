package com.glyph.core.stats;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Buffered statistics (GDD sections 30, 104): memory counters → periodic
 * async batch → PostgreSQL. Flushes periodically, per-player on disconnect,
 * and fully on shutdown. When the database is down, deltas are restored to
 * the buffer and retried on the next flush — never dropped.
 */
public final class StatsService {

    private final StatsBuffer buffer = new StatsBuffer();
    private final StatsRepository repository;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    public StatsService(
            StatsRepository repository,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Safe from any thread; never touches the database. */
    public void increment(UUID playerUuid, StatType type) {
        buffer.increment(playerUuid, type, 1);
    }

    public void increment(UUID playerUuid, StatType type, long amount) {
        buffer.increment(playerUuid, type, amount);
    }

    /** Periodic flush; also called once more during shutdown. */
    public void flushAll() {
        Map<UUID, Map<StatType, Long>> deltas = buffer.drain();
        if (deltas.isEmpty()) {
            return;
        }
        if (!databaseReady.getAsBoolean()) {
            buffer.restore(deltas);
            return;
        }
        try {
            repository.addDeltas(deltas);
        } catch (Exception e) {
            logger.error("Stats flush failed for {} player(s) — deltas kept for retry",
                    deltas.size(), e);
            buffer.restore(deltas);
        }
    }

    /** Disconnect flush for one player (async). */
    public void flushPlayerAsync(UUID playerUuid) {
        Map<StatType, Long> deltas = buffer.drainPlayer(playerUuid);
        if (deltas.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(
                        () -> repository.addDeltas(Map.of(playerUuid, deltas)), ioExecutor)
                .exceptionally(error -> {
                    logger.error("Stats flush on quit failed for {} — deltas kept for retry",
                            playerUuid, error);
                    buffer.restore(Map.of(playerUuid, deltas));
                    return null;
                });
    }

    /**
     * Read-through stats lookup: the player's pending deltas are flushed
     * first so /stats never lags behind gameplay.
     */
    public CompletableFuture<Optional<PlayerStats>> stats(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Statistics unavailable: database is down"));
        }
        return CompletableFuture.supplyAsync(() -> {
            Map<StatType, Long> pending = buffer.drainPlayer(playerUuid);
            if (!pending.isEmpty()) {
                try {
                    repository.addDeltas(Map.of(playerUuid, pending));
                } catch (Exception e) {
                    buffer.restore(Map.of(playerUuid, pending));
                    throw e;
                }
            }
            return repository.find(playerUuid);
        }, ioExecutor);
    }
}
