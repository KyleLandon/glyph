package com.glyph.api.health;

/**
 * Health state of a single infrastructure component.
 */
public enum HealthStatus {

    /** Component is connected and responding. */
    UP,

    /** Component has not finished initializing yet. */
    INITIALIZING,

    /** Component is unreachable or failing. */
    DOWN
}
