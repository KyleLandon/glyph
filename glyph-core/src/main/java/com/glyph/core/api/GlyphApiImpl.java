package com.glyph.core.api;

import com.glyph.api.GlyphApi;
import com.glyph.api.health.HealthApi;
import java.util.Objects;

/**
 * Canonical {@link GlyphApi} implementation registered by GlyphCore.
 */
public final class GlyphApiImpl implements GlyphApi {

    private final String serverId;
    private final HealthApi health;

    public GlyphApiImpl(String serverId, HealthApi health) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.health = Objects.requireNonNull(health, "health");
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public HealthApi health() {
        return health;
    }
}
