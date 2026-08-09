package com.glyph.api.health;

import java.util.concurrent.CompletableFuture;

/**
 * Queries the health of the platform's infrastructure components
 * (PostgreSQL, Redis, ...).
 *
 * <p>Thread context: {@link #check()} may be called from any thread and never
 * blocks the caller; the returned future completes on an async I/O thread.
 * Callers that need to touch Minecraft state with the result must hop back to
 * the appropriate region/entity scheduler.</p>
 */
public interface HealthApi {

    /**
     * Runs all registered component health checks.
     *
     * @return a future completing with the aggregated report; never completes exceptionally —
     *         failing components are reported as {@link HealthStatus#DOWN}
     */
    CompletableFuture<HealthReport> check();
}
