package com.glyph.core.api;

import com.glyph.api.GlyphApi;
import com.glyph.api.health.HealthApi;
import com.glyph.api.player.PlayerApi;
import java.util.Objects;

/**
 * Canonical {@link GlyphApi} implementation registered by GlyphCore.
 */
public final class GlyphApiImpl implements GlyphApi {

    private final String serverId;
    private final HealthApi health;
    private final PlayerApi players;

    public GlyphApiImpl(String serverId, HealthApi health, PlayerApi players) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.health = Objects.requireNonNull(health, "health");
        this.players = Objects.requireNonNull(players, "players");
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public HealthApi health() {
        return health;
    }

    @Override
    public PlayerApi players() {
        return players;
    }
}
