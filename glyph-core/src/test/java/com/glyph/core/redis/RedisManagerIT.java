package com.glyph.core.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.health.HealthStatus;
import com.glyph.core.config.RedisSettings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test: Redis connects, pings, health reports UP.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisManagerIT {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @AfterAll
    static void shutdownExecutor() {
        EXECUTOR.shutdownNow();
    }

    @Test
    void initConnectsAndHealthReportsUp() {
        RedisSettings settings = new RedisSettings(REDIS.getHost(), REDIS.getMappedPort(6379), "");

        try (RedisManager manager = new RedisManager(
                settings, LoggerFactory.getLogger("test"), EXECUTOR)) {

            assertThat(manager.check().join().status()).isEqualTo(HealthStatus.INITIALIZING);

            manager.initAsync().join();

            assertThat(manager.isReady()).isTrue();
            assertThat(manager.check().join().status()).isEqualTo(HealthStatus.UP);

            manager.connection().sync().set("glyph:test", "ok");
            assertThat(manager.connection().sync().get("glyph:test")).isEqualTo("ok");
        }
    }
}
