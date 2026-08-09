package com.glyph.core.player;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.player.PlayerProfile;
import com.glyph.core.config.DatabaseSettings;
import com.glyph.core.database.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the join/quit persistence flow against real PostgreSQL
 * (schema from Flyway migration V1). Skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresPlayerRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("glyph_test")
                    .withUsername("glyph_test")
                    .withPassword("glyph_test");

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static DatabaseManager manager;
    private static PostgresPlayerRepository repository;

    @BeforeAll
    static void initDatabase() {
        manager = new DatabaseManager(
                new DatabaseSettings(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        1, 4, 5_000, 60_000, 300_000),
                LoggerFactory.getLogger("test"), EXECUTOR);
        manager.initAsync().join();
        repository = new PostgresPlayerRepository(manager::dataSource);
    }

    @AfterAll
    static void shutdown() {
        if (manager != null) {
            manager.close();
        }
        EXECUTOR.shutdownNow();
    }

    @Test
    void firstJoinCreatesProfileAndEconomyAccount() throws Exception {
        UUID uuid = UUID.randomUUID();

        PlayerRepository.JoinResult result = repository.recordJoin(uuid, "Steve");

        assertThat(result.firstJoin()).isTrue();
        PlayerProfile profile = result.profile();
        assertThat(profile.uuid()).isEqualTo(uuid);
        assertThat(profile.username()).isEqualTo("Steve");
        assertThat(profile.firstJoin()).isEqualTo(profile.lastJoin());
        assertThat(profile.playtimeSeconds()).isZero();

        assertThat(accountCount(uuid)).isEqualTo(1);
        assertThat(accountBalance(uuid)).isZero();
    }

    @Test
    void rejoinUpdatesUsernameAndTimestampsButKeepsFirstJoinAndAccount() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerProfile first = repository.recordJoin(uuid, "Alex").profile();

        PlayerRepository.JoinResult rejoin = repository.recordJoin(uuid, "AlexRenamed");

        assertThat(rejoin.firstJoin()).isFalse();
        PlayerProfile updated = rejoin.profile();
        assertThat(updated.username()).isEqualTo("AlexRenamed");
        assertThat(updated.firstJoin()).isEqualTo(first.firstJoin());
        assertThat(updated.lastJoin()).isAfterOrEqualTo(first.lastJoin());
        assertThat(updated.lastSeen()).isAfterOrEqualTo(first.lastSeen());

        assertThat(accountCount(uuid)).isEqualTo(1);
    }

    @Test
    void quitAccumulatesPlaytimeAndAdvancesLastSeen() {
        UUID uuid = UUID.randomUUID();
        PlayerProfile joined = repository.recordJoin(uuid, "Notch").profile();

        repository.recordQuit(uuid, 90);
        repository.recordJoin(uuid, "Notch");
        repository.recordQuit(uuid, 30);

        PlayerProfile after = repository.findByUuid(uuid).orElseThrow();
        assertThat(after.playtimeSeconds()).isEqualTo(120);
        assertThat(after.lastSeen()).isAfterOrEqualTo(joined.lastSeen());
    }

    @Test
    void negativeSessionSecondsAreClampedToZero() {
        UUID uuid = UUID.randomUUID();
        repository.recordJoin(uuid, "Herobrine");

        repository.recordQuit(uuid, -500);

        assertThat(repository.findByUuid(uuid).orElseThrow().playtimeSeconds()).isZero();
    }

    @Test
    void findByUsernameIsCaseInsensitive() {
        UUID uuid = UUID.randomUUID();
        repository.recordJoin(uuid, "CasedName");

        assertThat(repository.findByUsername("casedname"))
                .isPresent()
                .hasValueSatisfying(p -> assertThat(p.uuid()).isEqualTo(uuid));
        assertThat(repository.findByUsername("someone-else")).isEmpty();
    }

    @Test
    void findByUuidReturnsEmptyForUnknownPlayer() {
        assertThat(repository.findByUuid(UUID.randomUUID())).isEmpty();
    }

    private static int accountCount(UUID owner) throws Exception {
        try (Connection connection = manager.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM accounts WHERE owner_type = 'PLAYER' AND owner_uuid = ?")) {
            statement.setObject(1, owner);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    private static long accountBalance(UUID owner) throws Exception {
        try (Connection connection = manager.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT balance FROM accounts WHERE owner_type = 'PLAYER' AND owner_uuid = ?")) {
            statement.setObject(1, owner);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }
}
