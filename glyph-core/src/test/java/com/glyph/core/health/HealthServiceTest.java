package com.glyph.core.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.health.ComponentHealth;
import com.glyph.api.health.HealthReport;
import com.glyph.api.health.HealthStatus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class HealthServiceTest {

    private static HealthCheck fixed(String name, ComponentHealth result) {
        return new HealthCheck() {
            @Override
            public String componentName() {
                return name;
            }

            @Override
            public CompletableFuture<ComponentHealth> check() {
                return CompletableFuture.completedFuture(result);
            }
        };
    }

    @Test
    void allUpReportsUp() {
        HealthService service = new HealthService(List.of(
                fixed("a", ComponentHealth.up("a", "", 1)),
                fixed("b", ComponentHealth.up("b", "", 2))));

        HealthReport report = service.check().join();

        assertThat(report.overall()).isEqualTo(HealthStatus.UP);
        assertThat(report.components()).hasSize(2);
    }

    @Test
    void anyDownReportsDown() {
        HealthService service = new HealthService(List.of(
                fixed("a", ComponentHealth.up("a", "", 1)),
                fixed("b", ComponentHealth.down("b", "boom"))));

        assertThat(service.check().join().overall()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void initializingWithoutFailuresReportsInitializing() {
        HealthService service = new HealthService(List.of(
                fixed("a", ComponentHealth.up("a", "", 1)),
                fixed("b", ComponentHealth.initializing("b"))));

        assertThat(service.check().join().overall()).isEqualTo(HealthStatus.INITIALIZING);
    }

    @Test
    void failingCheckFutureBecomesDownEntry() {
        HealthCheck failing = new HealthCheck() {
            @Override
            public String componentName() {
                return "broken";
            }

            @Override
            public CompletableFuture<ComponentHealth> check() {
                return CompletableFuture.failedFuture(new IllegalStateException("connection refused"));
            }
        };

        HealthReport report = new HealthService(List.of(failing)).check().join();

        assertThat(report.overall()).isEqualTo(HealthStatus.DOWN);
        assertThat(report.components().getFirst().detail()).contains("connection refused");
    }

    @Test
    void throwingCheckBecomesDownEntry() {
        HealthCheck throwing = new HealthCheck() {
            @Override
            public String componentName() {
                return "throwing";
            }

            @Override
            public CompletableFuture<ComponentHealth> check() {
                throw new IllegalStateException("immediate failure");
            }
        };

        HealthReport report = new HealthService(List.of(throwing)).check().join();

        assertThat(report.overall()).isEqualTo(HealthStatus.DOWN);
        assertThat(report.components().getFirst().detail()).contains("immediate failure");
    }
}
