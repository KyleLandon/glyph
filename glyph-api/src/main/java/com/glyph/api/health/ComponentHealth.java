package com.glyph.api.health;

import java.util.Objects;

/**
 * Health check result for a single infrastructure component.
 *
 * @param component     stable component name, e.g. {@code postgresql} or {@code redis}
 * @param status        current status
 * @param detail        human-readable detail (error message, version info, ...); may be empty
 * @param latencyMillis round-trip latency of the check, or {@code -1} if not applicable
 */
public record ComponentHealth(String component, HealthStatus status, String detail, long latencyMillis) {

    public ComponentHealth {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    public static ComponentHealth up(String component, String detail, long latencyMillis) {
        return new ComponentHealth(component, HealthStatus.UP, detail, latencyMillis);
    }

    public static ComponentHealth down(String component, String detail) {
        return new ComponentHealth(component, HealthStatus.DOWN, detail, -1);
    }

    public static ComponentHealth initializing(String component) {
        return new ComponentHealth(component, HealthStatus.INITIALIZING, "still starting", -1);
    }
}
