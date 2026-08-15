package com.glyph.core.redis;

import com.glyph.api.health.ComponentHealth;
import com.glyph.core.config.RedisSettings;
import com.glyph.core.health.HealthCheck;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * Owns the Lettuce Redis client and connection.
 *
 * <p>Redis is a cache and messaging layer only — never the authoritative
 * store for money or player data (GDD sections 58, 60, 87). Initialization is
 * asynchronous; Redis being down must never prevent survival gameplay.</p>
 */
public final class RedisManager implements HealthCheck, AutoCloseable {

    private final RedisSettings settings;
    private final Logger logger;
    private final Executor ioExecutor;

    private volatile RedisClient client;
    private volatile StatefulRedisConnection<String, String> connection;
    private volatile boolean ready;

    public RedisManager(RedisSettings settings, Logger logger, Executor ioExecutor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
    }

    /**
     * Connects and verifies the connection with a PING, on an async thread.
     */
    public CompletableFuture<Void> initAsync() {
        return CompletableFuture.runAsync(() -> {
            RedisURI.Builder uri = RedisURI.builder()
                    .withHost(settings.host())
                    .withPort(settings.port());
            if (settings.hasPassword()) {
                uri.withPassword(settings.password().toCharArray());
            }

            RedisClient newClient = RedisClient.create(uri.build());
            try {
                StatefulRedisConnection<String, String> newConnection = newClient.connect();
                newConnection.sync().ping();
                this.client = newClient;
                this.connection = newConnection;
                this.ready = true;
                logger.info("Redis ready: {}:{}", settings.host(), settings.port());
            } catch (Exception e) {
                newClient.shutdown();
                throw e;
            }
        }, ioExecutor);
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * @return the live connection
     * @throws IllegalStateException if initialization has not completed
     */
    public StatefulRedisConnection<String, String> connection() {
        StatefulRedisConnection<String, String> current = this.connection;
        if (current == null) {
            throw new IllegalStateException("Redis connection is not initialized");
        }
        return current;
    }

    /**
     * Best-effort pub/sub publish. Never throws to callers — Redis outages must
     * not break gameplay. No-ops when Redis is not ready.
     */
    public void publish(String channel, String message) {
        if (!ready) {
            return;
        }
        try {
            connection().sync().publish(channel, message);
        } catch (Exception e) {
            logger.warn("Redis publish failed on channel {}: {}", channel, e.toString());
        }
    }

    @Override
    public String componentName() {
        return "redis";
    }

    @Override
    public CompletableFuture<ComponentHealth> check() {
        if (!ready) {
            return CompletableFuture.completedFuture(ComponentHealth.initializing(componentName()));
        }
        long start = System.nanoTime();
        return connection().async().ping().toCompletableFuture()
                .thenApply(pong -> {
                    long latencyMs = (System.nanoTime() - start) / 1_000_000;
                    return ComponentHealth.up(componentName(),
                            settings.host() + ":" + settings.port(), latencyMs);
                });
    }

    @Override
    public void close() {
        ready = false;
        StatefulRedisConnection<String, String> currentConnection = this.connection;
        if (currentConnection != null) {
            currentConnection.close();
            this.connection = null;
        }
        RedisClient currentClient = this.client;
        if (currentClient != null) {
            currentClient.shutdown();
            this.client = null;
            logger.info("Redis connection closed");
        }
    }
}
