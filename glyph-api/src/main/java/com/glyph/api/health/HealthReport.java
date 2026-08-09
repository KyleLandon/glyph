package com.glyph.api.health;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated health of all platform components at a point in time.
 *
 * @param checkedAt  when the report was produced
 * @param components individual component results, in registration order
 */
public record HealthReport(Instant checkedAt, List<ComponentHealth> components) {

    public HealthReport {
        components = List.copyOf(components);
    }

    /**
     * @return {@link HealthStatus#UP} only if every component is up;
     *         {@link HealthStatus#INITIALIZING} if any component is still starting and none are down;
     *         {@link HealthStatus#DOWN} if any component is down
     */
    public HealthStatus overall() {
        HealthStatus overall = HealthStatus.UP;
        for (ComponentHealth component : components) {
            if (component.status() == HealthStatus.DOWN) {
                return HealthStatus.DOWN;
            }
            if (component.status() == HealthStatus.INITIALIZING) {
                overall = HealthStatus.INITIALIZING;
            }
        }
        return overall;
    }
}
