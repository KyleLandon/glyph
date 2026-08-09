package com.glyph.core.player;

import com.glyph.api.player.PlayerApi;
import com.glyph.api.player.PlayerProfile;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Player identity layer (GDD sections 100 and 133).
 *
 * <p>Every database call runs on the async executor; join/quit handlers return
 * immediately and never throw, so a disconnect can never generate an unhandled
 * exception on a tick thread. When the database is down, joins and quits are
 * logged and skipped — gameplay continues, identity resumes with the next
 * event once the database is back (PostgreSQL stamps last_join/last_seen with
 * its own clock, so late writes stay consistent).</p>
 */
public final class PlayerService implements PlayerApi {

    private final PlayerRepository repository;
    private final PlayerSessionService sessions;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    /** Profiles of currently online players, kept fresh by join upserts. */
    private final Map<UUID, PlayerProfile> onlineProfiles = new ConcurrentHashMap<>();

    public PlayerService(
            PlayerRepository repository,
            PlayerSessionService sessions,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Handles a join: starts session tracking immediately, then upserts the
     * player row (and economy account on first join) asynchronously.
     *
     * @return {@code true} when this join created the player row; failures and
     *         skipped persistence yield {@code false}
     */
    public CompletableFuture<Boolean> handleJoin(UUID uuid, String username) {
        sessions.beginSession(uuid);

        if (!databaseReady.getAsBoolean()) {
            logger.warn("Join of {} ({}) not persisted: database unavailable", username, uuid);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture
                .supplyAsync(() -> repository.recordJoin(uuid, username), ioExecutor)
                .thenApply(result -> {
                    onlineProfiles.put(uuid, result.profile());
                    if (result.firstJoin()) {
                        logger.info("First join: {} ({}) — profile and economy account created",
                                username, uuid);
                    } else {
                        logger.debug("Join persisted for {} ({})", username, uuid);
                    }
                    return result.firstJoin();
                })
                .exceptionally(error -> {
                    logger.error("Failed to persist join of {} ({})", username, uuid, error);
                    return false;
                });
    }

    /** Top players by accumulated playtime (persisted seconds only). */
    public CompletableFuture<List<PlayerRepository.PlaytimeLeader>> topPlaytime(int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Playtime leaderboard unavailable: database is down"));
        }
        return CompletableFuture.supplyAsync(() -> repository.topPlaytime(capped), ioExecutor);
    }

    /**
     * Handles a quit: ends session tracking, then persists last_seen and
     * playtime asynchronously.
     *
     * @return future for tests and composition; failures are already logged
     */
    public CompletableFuture<Void> handleQuit(UUID uuid, String username) {
        long sessionSeconds = sessions.endSession(uuid)
                .map(duration -> duration.toSeconds())
                .orElse(0L);
        onlineProfiles.remove(uuid);

        if (!databaseReady.getAsBoolean()) {
            logger.warn("Quit of {} ({}) not persisted: database unavailable", username, uuid);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture
                .runAsync(() -> repository.recordQuit(uuid, sessionSeconds), ioExecutor)
                .exceptionally(error -> {
                    logger.error("Failed to persist quit of {} ({})", username, uuid, error);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> byUuid(UUID uuid) {
        PlayerProfile online = onlineProfiles.get(uuid);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online));
        }
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Player lookup unavailable: database is down"));
        }
        return CompletableFuture.supplyAsync(() -> repository.findByUuid(uuid), ioExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> byUsername(String username) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Player lookup unavailable: database is down"));
        }
        return CompletableFuture.supplyAsync(() -> repository.findByUsername(username), ioExecutor);
    }

    /** @return the cached profile of an online player, if the join upsert has completed */
    public Optional<PlayerProfile> onlineProfile(UUID uuid) {
        return Optional.ofNullable(onlineProfiles.get(uuid));
    }
}
