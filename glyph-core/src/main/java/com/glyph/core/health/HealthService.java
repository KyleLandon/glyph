package com.glyph.core.health;

import com.glyph.api.health.ComponentHealth;
import com.glyph.api.health.HealthApi;
import com.glyph.api.health.HealthReport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Aggregates all registered {@link HealthCheck}s into a {@link HealthReport}.
 *
 * <p>Individual check failures and timeouts are converted into
 * {@code DOWN} entries; the returned future never completes exceptionally.</p>
 */
public final class HealthService implements HealthApi {

    private static final long CHECK_TIMEOUT_SECONDS = 5;

    private final List<HealthCheck> checks;

    public HealthService(List<HealthCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    @Override
    public CompletableFuture<HealthReport> check() {
        List<CompletableFuture<ComponentHealth>> futures = checks.stream()
                .map(this::safeCheck)
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> new HealthReport(
                        Instant.now(),
                        futures.stream().map(CompletableFuture::join).toList()));
    }

    private CompletableFuture<ComponentHealth> safeCheck(HealthCheck check) {
        CompletableFuture<ComponentHealth> future;
        try {
            future = check.check();
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ComponentHealth.down(check.componentName(), rootMessage(e)));
        }
        return future
                .orTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(e -> ComponentHealth.down(check.componentName(), rootMessage(e)));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
