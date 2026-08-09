package com.glyph.core.player;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live sessions in memory so quit events can compute how long the
 * player was online. Purely in-memory and thread-safe: join and quit events
 * arrive on different Folia threads.
 */
public final class PlayerSessionService {

    private final InstantSource clock;
    private final Map<UUID, Instant> sessionStarts = new ConcurrentHashMap<>();

    public PlayerSessionService() {
        this(InstantSource.system());
    }

    PlayerSessionService(InstantSource clock) {
        this.clock = clock;
    }

    /** Marks the start of a session, replacing any stale entry for the UUID. */
    public void beginSession(UUID uuid) {
        sessionStarts.put(uuid, clock.instant());
    }

    /**
     * Ends a session and returns its duration, or empty if no session was
     * being tracked (e.g. the plugin was reloaded mid-session). Never negative.
     */
    public Optional<Duration> endSession(UUID uuid) {
        Instant start = sessionStarts.remove(uuid);
        if (start == null) {
            return Optional.empty();
        }
        Duration duration = Duration.between(start, clock.instant());
        return Optional.of(duration.isNegative() ? Duration.ZERO : duration);
    }

    public boolean hasSession(UUID uuid) {
        return sessionStarts.containsKey(uuid);
    }

    public int activeSessions() {
        return sessionStarts.size();
    }
}
