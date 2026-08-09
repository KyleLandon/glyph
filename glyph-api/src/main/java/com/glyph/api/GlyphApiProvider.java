package com.glyph.api;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Static access point for the {@link GlyphApi} instance registered by GlyphCore.
 */
public final class GlyphApiProvider {

    private static final AtomicReference<GlyphApi> INSTANCE = new AtomicReference<>();

    private GlyphApiProvider() {
    }

    /**
     * @return the registered API instance
     * @throws IllegalStateException if GlyphCore has not been enabled yet
     */
    public static GlyphApi get() {
        GlyphApi api = INSTANCE.get();
        if (api == null) {
            throw new IllegalStateException(
                    "GlyphApi is not available. GlyphCore is not enabled (or has been disabled).");
        }
        return api;
    }

    /**
     * Registers the API implementation. Called by GlyphCore on enable.
     */
    public static void register(GlyphApi api) {
        if (!INSTANCE.compareAndSet(null, api)) {
            throw new IllegalStateException("GlyphApi is already registered");
        }
    }

    /**
     * Unregisters the API implementation. Called by GlyphCore on disable.
     */
    public static void unregister() {
        INSTANCE.set(null);
    }
}
