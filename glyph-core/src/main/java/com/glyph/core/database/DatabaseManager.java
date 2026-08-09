package com.glyph.core.database;

import com.glyph.api.health.ComponentHealth;
import com.glyph.core.config.DatabaseSettings;
import com.glyph.core.health.HealthCheck;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;

/**
 * Owns the HikariCP connection pool and Flyway migrations.
 *
 * <p>Initialization is fully asynchronous ({@link #initAsync()}); nothing here
 * may run on a Minecraft region/entity tick thread. PostgreSQL is the source
 * of truth for all persistent state (GDD section 60).</p>
 */
public final class DatabaseManager implements HealthCheck, AutoCloseable {

    private final DatabaseSettings settings;
    private final Logger logger;
    private final Executor ioExecutor;

    private volatile HikariDataSource dataSource;
    private volatile boolean ready;

    public DatabaseManager(DatabaseSettings settings, Logger logger, Executor ioExecutor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
    }

    /**
     * Opens the connection pool and applies pending Flyway migrations on an
     * async thread. Completes exceptionally if the database is unreachable or
     * a migration fails; the manager then stays in a not-ready state and
     * health checks report DOWN.
     */
    public CompletableFuture<Void> initAsync() {
        return CompletableFuture.runAsync(() -> {
            HikariConfig config = new HikariConfig();
            config.setPoolName("glyph-postgres");
            // Explicit driver class: DriverManager auto-discovery does not see
            // drivers loaded by Paper's per-plugin library classloader.
            config.setDriverClassName("org.postgresql.Driver");
            config.setJdbcUrl(settings.jdbcUrl());
            config.setUsername(settings.username());
            config.setPassword(settings.password());
            config.setMinimumIdle(settings.minimumIdle());
            config.setMaximumPoolSize(settings.maximumPoolSize());
            config.setConnectionTimeout(settings.connectionTimeoutMs());
            config.setIdleTimeout(settings.idleTimeoutMs());
            config.setMaxLifetime(settings.maxLifetimeMs());

            HikariDataSource pool = new HikariDataSource(config);
            try {
                Flyway flyway = Flyway.configure(DatabaseManager.class.getClassLoader())
                        .dataSource(pool)
                        .locations("classpath:db/migration")
                        // LuckPerms shares this database (docs/LOCAL_TEST_SERVER.md), so on a
                        // fresh machine its tables may exist before our first migration runs.
                        // Baseline at 0 keeps Flyway happy with the non-empty schema while
                        // still applying every real migration (V1+).
                        .baselineOnMigrate(true)
                        .baselineVersion("0")
                        .load();
                MigrateResult result = flyway.migrate();
                String schemaVersion = flyway.info().current() != null
                        ? flyway.info().current().getVersion().toString() : "none";
                if (result.migrationsExecuted > 0) {
                    logger.info("Database ready: {} migration(s) applied, schema version {}",
                            result.migrationsExecuted, schemaVersion);
                } else {
                    logger.info("Database ready: schema up to date (version {})", schemaVersion);
                }
            } catch (Exception e) {
                pool.close();
                throw e;
            }

            this.dataSource = pool;
            this.ready = true;
        }, ioExecutor);
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * @return the live pool
     * @throws IllegalStateException if initialization has not completed
     */
    public DataSource dataSource() {
        DataSource current = this.dataSource;
        if (current == null) {
            throw new IllegalStateException("Database pool is not initialized");
        }
        return current;
    }

    @Override
    public String componentName() {
        return "postgresql";
    }

    @Override
    public CompletableFuture<ComponentHealth> check() {
        if (!ready) {
            return CompletableFuture.completedFuture(ComponentHealth.initializing(componentName()));
        }
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            try (Connection connection = dataSource().getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                resultSet.next();
                long latencyMs = (System.nanoTime() - start) / 1_000_000;
                return ComponentHealth.up(componentName(), settings.host() + ":" + settings.port(), latencyMs);
            } catch (Exception e) {
                return ComponentHealth.down(componentName(), e.getMessage() == null
                        ? e.getClass().getSimpleName() : e.getMessage());
            }
        }, ioExecutor);
    }

    @Override
    public void close() {
        ready = false;
        HikariDataSource current = this.dataSource;
        if (current != null) {
            current.close();
            this.dataSource = null;
            logger.info("Database pool closed");
        }
    }
}
