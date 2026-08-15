package com.glyph.core.glyphs;

/**
 * Wraps checked {@link java.sql.SQLException}s from the glyphs repository.
 */
public final class GlyphsPersistenceException extends RuntimeException {

    public GlyphsPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
