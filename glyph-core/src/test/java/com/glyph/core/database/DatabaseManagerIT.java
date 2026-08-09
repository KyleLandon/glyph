package com.glyph.core.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glyph.api.health.HealthStatus;
import com.glyph.core.config.DatabaseSettings;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test: pool opens, Flyway migrates, health reports UP.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseManagerIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("glyph_test")
                    .withUsername("glyph_test")
                    .withPassword("glyph_test");

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @AfterAll
    static void shutdownExecutor() {
        EXECUTOR.shutdownNow();
    }

    private static DatabaseSettings settingsFor(PostgreSQLContainer<?> container) {
        return new DatabaseSettings(
                container.getHost(),
                container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                container.getDatabaseName(),
                container.getUsername(),
                container.getPassword(),
                1, 4, 5_000, 60_000, 300_000);
    }

    @Test
    void initRunsMigrationsAndHealthReportsUp() throws Exception {
        try (DatabaseManager manager = new DatabaseManager(
                settingsFor(POSTGRES), LoggerFactory.getLogger("test"), EXECUTOR)) {

            assertThat(manager.isReady()).isFalse();
            assertThat(manager.check().join().status()).isEqualTo(HealthStatus.INITIALIZING);

            manager.initAsync().join();

            assertThat(manager.isReady()).isTrue();
            assertThat(manager.check().join().status()).isEqualTo(HealthStatus.UP);

            try (Connection connection = manager.dataSource().getConnection();
                 ResultSet tables = connection.createStatement().executeQuery("""
                         SELECT table_name FROM information_schema.tables
                         WHERE table_schema = 'public'
                         ORDER BY table_name
                         """)) {
                var names = new java.util.ArrayList<String>();
                while (tables.next()) {
                    names.add(tables.getString(1));
                }
                assertThat(names).containsAll(
                        List.of("accounts", "flyway_schema_history", "players", "transactions"));
            }
        }
    }

    @Test
    void unreachableDatabaseFailsInitWithoutThrowingOnCaller() {
        DatabaseSettings bad = new DatabaseSettings(
                "localhost", 1, "nope", "nope", "nope", 1, 2, 500, 60_000, 300_000);

        try (DatabaseManager manager = new DatabaseManager(
                bad, LoggerFactory.getLogger("test"), EXECUTOR)) {

            assertThatThrownBy(() -> manager.initAsync().join())
                    .isInstanceOf(CompletionException.class);
            assertThat(manager.isReady()).isFalse();
        }
    }
}
