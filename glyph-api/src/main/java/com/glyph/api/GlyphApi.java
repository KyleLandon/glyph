package com.glyph.api;

import com.glyph.api.health.HealthApi;
import com.glyph.api.player.PlayerApi;

/**
 * Entry point to the Glyph platform API.
 *
 * <p>Obtain an instance via {@link GlyphApiProvider#get()}. Implementations are
 * registered by the GlyphCore plugin; other plugins must never implement this
 * interface themselves.</p>
 *
 * <p>Gameplay APIs (economy, auctions, bounties, statistics) will be added here
 * in later phases.</p>
 */
public interface GlyphApi {

    /**
     * @return the configured identifier of this backend server (e.g. {@code glyph-01})
     */
    String serverId();

    /**
     * @return access to infrastructure health checks
     */
    HealthApi health();

    /**
     * @return access to player identity data (Phase 2)
     */
    PlayerApi players();
}
