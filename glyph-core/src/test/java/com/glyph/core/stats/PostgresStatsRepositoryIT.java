package com.glyph.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.glyph.core.config.DatabaseSettings;
import com.glyph.core.database.DatabaseManager;
import com.glyph.core.player.PostgresPlayerRepository;
import java.util.Map;
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
 * Batched stat upserts against real PostgreSQL (GDD section 104).
 * Skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresStatsRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("glyph_test")
                    .withUsername("glyph_test")
                    .withPassword("glyph_test");

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static DatabaseManager manager;
    private static PostgresPlayerRepository players;
    private static PostgresStatsRepository stats;

    @BeforeAll
    static void initDatabase() {
        manager = new DatabaseManager(
                new DatabaseSettings(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        2, 8, 5_000, 60_000, 300_000),
                LoggerFactory.getLogger("test"), EXECUTOR);
        manager.initAsync().join();
        players = new PostgresPlayerRepository(manager::dataSource, 0);
        stats = new PostgresStatsRepository(manager::dataSource);
    }

    @AfterAll
    static void shutdown() {
        if (manager != null) {
            manager.close();
        }
        EXECUTOR.shutdownNow();
    }

    private static UUID joinedPlayer() {
        UUID uuid = UUID.randomUUID();
        players.recordJoin(uuid, "P" + uuid.toString().substring(0, 8));
        return uuid;
    }

    @Test
    void firstFlushCreatesRowSecondAccumulates() {
        UUID player = joinedPlayer();

        stats.addDeltas(Map.of(player, Map.of(
                StatType.BLOCKS_BROKEN, 10L, StatType.KILLS, 2L)));
        stats.addDeltas(Map.of(player, Map.of(
                StatType.BLOCKS_BROKEN, 5L, StatType.DEATHS, 1L)));

        PlayerStats row = stats.find(player).orElseThrow();
        assertThat(row.blocksBroken()).isEqualTo(15);
        assertThat(row.kills()).isEqualTo(2);
        assertThat(row.deaths()).isEqualTo(1);
        assertThat(row.mobKills()).isZero();
    }

    @Test
    void batchFlushWritesMultiplePlayers() {
        UUID alice = joinedPlayer();
        UUID bob = joinedPlayer();

        stats.addDeltas(Map.of(
                alice, Map.of(StatType.DISTANCE_CM, 123_456L),
                bob, Map.of(StatType.MOB_KILLS, 7L)));

        assertThat(stats.find(alice).orElseThrow().distanceCm()).isEqualTo(123_456);
        assertThat(stats.find(bob).orElseThrow().mobKills()).isEqualTo(7);
    }

    @Test
    void unknownPlayerHasNoRow() {
        assertThat(stats.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void serviceReadThroughFlushesPendingDeltas() {
        UUID player = joinedPlayer();
        StatsService service = new StatsService(
                stats, () -> true, EXECUTOR, LoggerFactory.getLogger("test"));

        service.increment(player, StatType.KILLS);
        service.increment(player, StatType.BLOCKS_PLACED, 4);

        PlayerStats row = service.stats(player).join().orElseThrow();
        assertThat(row.kills()).isEqualTo(1);
        assertThat(row.blocksPlaced()).isEqualTo(4);
    }

    @Test
    void topOrdersByKillsAndJoinsUsername() {
        // Fixed names so ORDER BY value DESC, lower(username) ASC is deterministic on ties.
        UUID alice = joinedAs("Alice");
        UUID bob = joinedAs("Bob");
        UUID carol = joinedAs("Carol");

        stats.addDeltas(Map.of(
                alice, Map.of(StatType.KILLS, 5L),
                bob, Map.of(StatType.KILLS, 12L),
                carol, Map.of(StatType.KILLS, 12L, StatType.DEATHS, 3L)));

        assertThat(stats.top(StatType.KILLS, 2))
                .extracting(StatLeader::uuid, StatLeader::username, StatLeader::value)
                .containsExactly(
                        tuple(bob, "Bob", 12L),
                        tuple(carol, "Carol", 12L));

        assertThat(stats.top(StatType.DEATHS, 10))
                .extracting(StatLeader::uuid, StatLeader::value)
                .contains(tuple(carol, 3L));
    }

    private static UUID joinedAs(String username) {
        UUID uuid = UUID.randomUUID();
        players.recordJoin(uuid, username);
        return uuid;
    }
}
