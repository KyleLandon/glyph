package com.glyph.core.player;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerSessionServiceTest {

    /** Manually advanced clock so durations are deterministic. */
    private static final class FakeClock implements InstantSource {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private final FakeClock clock = new FakeClock();
    private final PlayerSessionService sessions = new PlayerSessionService(clock);
    private final UUID uuid = UUID.randomUUID();

    @Test
    void endSessionReturnsElapsedDuration() {
        sessions.beginSession(uuid);
        clock.advance(Duration.ofMinutes(42));

        assertThat(sessions.endSession(uuid)).contains(Duration.ofMinutes(42));
        assertThat(sessions.hasSession(uuid)).isFalse();
    }

    @Test
    void endSessionWithoutJoinReturnsEmpty() {
        assertThat(sessions.endSession(uuid)).isEmpty();
    }

    @Test
    void rejoinRestartsTheSession() {
        sessions.beginSession(uuid);
        clock.advance(Duration.ofHours(1));
        sessions.beginSession(uuid);
        clock.advance(Duration.ofMinutes(5));

        assertThat(sessions.endSession(uuid)).contains(Duration.ofMinutes(5));
    }

    @Test
    void sessionEndedTwiceReturnsEmptySecondTime() {
        sessions.beginSession(uuid);
        assertThat(sessions.endSession(uuid)).isPresent();
        assertThat(sessions.endSession(uuid)).isEmpty();
    }

    @Test
    void activeSessionsCountsDistinctPlayers() {
        sessions.beginSession(UUID.randomUUID());
        sessions.beginSession(UUID.randomUUID());

        assertThat(sessions.activeSessions()).isEqualTo(2);
    }
}
