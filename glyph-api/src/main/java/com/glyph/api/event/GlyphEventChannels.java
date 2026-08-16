package com.glyph.api.event;

/**
 * Redis pub/sub channel names shared by GlyphCore, GlyphDiscord, and later GlyphWeb.
 */
public final class GlyphEventChannels {

    /** JSON events: glyph.lifetime, glyph.title, discord.linked, … */
    public static final String EVENTS = "glyph.events";

    private GlyphEventChannels() {
    }
}
