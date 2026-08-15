package com.glyph.core.glyphs;

/**
 * A purchasable Glyph shop item. {@code payload} meaning depends on {@link GlyphProductType}:
 * color name, title text, or death-style id.
 */
public record GlyphProduct(
        String id,
        GlyphProductType type,
        String displayName,
        long cost,
        String payload) {
}
