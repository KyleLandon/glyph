package com.glyph.core.health;

import com.glyph.api.health.ComponentHealth;
import java.util.concurrent.CompletableFuture;

/**
 * A single infrastructure component that can report its health.
 */
public interface HealthCheck {

    /** Stable component name used in reports, e.g. {@code postgresql}. */
    String componentName();

    /**
     * Performs the check without blocking the calling thread.
     * Implementations must complete the future on an async executor.
     */
    CompletableFuture<ComponentHealth> check();
}
