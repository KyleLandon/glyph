package com.glyph.api;

import com.glyph.api.health.HealthApi;

/**
 * Entry point to the Glyph platform API.
 *
 * <p>Obtain an instance via {@link GlyphApiProvider#get()}. Implementations are
 * registered by the GlyphCore plugin; other plugins must never implement this
 * interface themselves.</p>
 *
 * <p>Gameplay APIs (economy, auctions, bounties, statistics) will be added here
 * in later phases. Phase 1 exposes only platform health.</p>
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
}
