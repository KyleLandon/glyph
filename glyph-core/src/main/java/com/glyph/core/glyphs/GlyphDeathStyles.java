package com.glyph.core.glyphs;

import java.util.Map;
import java.util.Optional;

/** Death message style templates — payload ids from {@link GlyphCatalog}. */
public final class GlyphDeathStyles {

    private static final Map<String, String> TEMPLATES = Map.of(
            "fell", "{victim} fell before {killer}",
            "claimed", "{killer} claimed {victim}'s life.",
            "silence", "{victim} was silenced by {killer}.",
            "glyph", "{killer} etched {victim} into Glyph history.");

    private GlyphDeathStyles() {
    }

    public static Optional<String> templateForProductId(String productId) {
        return GlyphCatalog.find(productId)
                .filter(product -> product.type() == GlyphProductType.DEATH_MESSAGE)
                .map(GlyphProduct::payload)
                .flatMap(GlyphDeathStyles::templateForStyleId);
    }

    public static Optional<String> templateForStyleId(String styleId) {
        return Optional.ofNullable(TEMPLATES.get(styleId));
    }

    public static String format(String template, String victimName, String killerName) {
        return template
                .replace("{victim}", victimName)
                .replace("{killer}", killerName);
    }
}
