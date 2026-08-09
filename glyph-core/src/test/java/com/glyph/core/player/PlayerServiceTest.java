package com.glyph.core.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glyph.api.player.PlayerProfile;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit tests with an in-memory repository. The executor is same-thread so
 * assertions run deterministically; async behavior itself is covered by the
 * repository integration test.
 */
class PlayerServiceTest {

    private static final class InMemoryRepository implements PlayerRepository {
        final Map<UUID, PlayerProfile> rows = new HashMap<>();
        final Map<UUID, Long> quitSeconds = new HashMap<>();
        boolean failNextCall;

        @Override
        public JoinResult recordJoin(UUID uuid, String username) {
            maybeFail();
            Instant now = Instant.now();
            PlayerProfile existing = rows.get(uuid);
            boolean first = existing == null;
            PlayerProfile updated = first
                    ? new PlayerProfile(uuid, username, now, now, now, 0)
                    : new PlayerProfile(uuid, username, existing.firstJoin(), now, now,
                            existing.playtimeSeconds());
            rows.put(uuid, updated);
            return new JoinResult(updated, first);
        }

        @Override
        public void recordQuit(UUID uuid, long sessionSeconds) {
            maybeFail();
            quitSeconds.merge(uuid, sessionSeconds, Long::sum);
        }

        @Override
        public Optional<PlayerProfile> findByUuid(UUID uuid) {
            maybeFail();
            return Optional.ofNullable(rows.get(uuid));
        }

        @Override
        public Optional<PlayerProfile> findByUsername(String username) {
            maybeFail();
            return rows.values().stream()
                    .filter(p -> p.username().equalsIgnoreCase(username))
                    .findFirst();
        }

        private void maybeFail() {
            if (failNextCall) {
                failNextCall = false;
                throw new PlayerPersistenceException("simulated outage",
                        new RuntimeException("connection refused"));
            }
        }
    }

    private final InMemoryRepository repository = new InMemoryRepository();
    private final PlayerSessionService sessions = new PlayerSessionService();
    private final AtomicBoolean databaseReady = new AtomicBoolean(true);
    private final PlayerService service = new PlayerService(
            repository, sessions, databaseReady::get, Runnable::run,
            LoggerFactory.getLogger("test"));

    private final UUID uuid = UUID.randomUUID();

    @Test
    void joinPersistsProfileAndCachesIt() {
        service.handleJoin(uuid, "Steve").join();

        assertThat(repository.rows).containsKey(uuid);
        assertThat(service.onlineProfile(uuid)).isPresent();
        assertThat(service.onlineProfile(uuid).orElseThrow().username()).isEqualTo("Steve");
        assertThat(sessions.hasSession(uuid)).isTrue();
    }

    @Test
    void rejoinUpdatesUsername() {
        service.handleJoin(uuid, "Steve").join();
        service.handleQuit(uuid, "Steve").join();
        service.handleJoin(uuid, "SteveRenamed").join();

        assertThat(repository.rows.get(uuid).username()).isEqualTo("SteveRenamed");
    }

    @Test
    void quitEndsSessionPersistsPlaytimeAndEvictsCache() {
        service.handleJoin(uuid, "Steve").join();
        service.handleQuit(uuid, "Steve").join();

        assertThat(repository.quitSeconds).containsKey(uuid);
        assertThat(service.onlineProfile(uuid)).isEmpty();
        assertThat(sessions.hasSession(uuid)).isFalse();
    }

    @Test
    void joinWithDatabaseDownDoesNotThrowAndStillTracksSession() {
        databaseReady.set(false);

        service.handleJoin(uuid, "Steve").join();

        assertThat(repository.rows).isEmpty();
        assertThat(sessions.hasSession(uuid)).isTrue();
    }

    @Test
    void quitWithDatabaseDownDoesNotThrow() {
        service.handleJoin(uuid, "Steve").join();
        databaseReady.set(false);

        service.handleQuit(uuid, "Steve").join();

        assertThat(repository.quitSeconds).isEmpty();
        assertThat(sessions.hasSession(uuid)).isFalse();
    }

    @Test
    void repositoryFailureOnJoinIsSwallowed() {
        repository.failNextCall = true;

        // Must complete normally: listener threads never see the exception.
        service.handleJoin(uuid, "Steve").join();

        assertThat(service.onlineProfile(uuid)).isEmpty();
    }

    @Test
    void repositoryFailureOnQuitIsSwallowed() {
        service.handleJoin(uuid, "Steve").join();
        repository.failNextCall = true;

        service.handleQuit(uuid, "Steve").join();

        assertThat(repository.quitSeconds).isEmpty();
    }

    @Test
    void byUuidServesOnlinePlayersFromCache() {
        service.handleJoin(uuid, "Steve").join();
        repository.failNextCall = true; // would explode if the DB were hit

        assertThat(service.byUuid(uuid).join()).isPresent();
        assertThat(repository.failNextCall).isTrue();
    }

    @Test
    void byUuidFallsBackToRepositoryForOfflinePlayers() {
        service.handleJoin(uuid, "Steve").join();
        service.handleQuit(uuid, "Steve").join();

        assertThat(service.byUuid(uuid).join()).isPresent();
    }

    @Test
    void lookupsFailFastWhenDatabaseDown() {
        databaseReady.set(false);

        assertThatThrownBy(() -> service.byUuid(uuid).join())
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.byUsername("Steve").join())
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
