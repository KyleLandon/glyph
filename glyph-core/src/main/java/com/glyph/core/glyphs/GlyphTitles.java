package com.glyph.core.glyphs;

import java.util.Map;
import java.util.Optional;

/** Resolves display text for shop and achievement title unlock ids. */
public final class GlyphTitles {

    private static final Map<String, String> ACHIEVEMENT_TITLES = Map.of(
            "title_blooded", "Blooded",
            "title_hunter", "Bounty Hunter",
            "title_broker", "Broker");

    private GlyphTitles() {
    }

    public static Optional<String> displayText(String unlockId) {
        if (unlockId == null || unlockId.isBlank()) {
            return Optional.empty();
        }
        return GlyphCatalog.find(unlockId)
                .filter(product -> product.type() == GlyphProductType.TITLE)
                .map(GlyphProduct::payload)
                .or(() -> Optional.ofNullable(ACHIEVEMENT_TITLES.get(unlockId)));
    }

    public static boolean isTitleUnlock(String unlockId) {
        return displayText(unlockId).isPresent();
    }
}
