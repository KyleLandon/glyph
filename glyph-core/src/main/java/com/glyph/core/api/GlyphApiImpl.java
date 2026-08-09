package com.glyph.core.api;

import com.glyph.api.GlyphApi;
import com.glyph.api.economy.EconomyApi;
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
    private final EconomyApi economy;

    public GlyphApiImpl(String serverId, HealthApi health, PlayerApi players, EconomyApi economy) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.health = Objects.requireNonNull(health, "health");
        this.players = Objects.requireNonNull(players, "players");
        this.economy = Objects.requireNonNull(economy, "economy");
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

    @Override
    public EconomyApi economy() {
        return economy;
    }
}
