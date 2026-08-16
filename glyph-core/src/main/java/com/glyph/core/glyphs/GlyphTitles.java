package com.glyph.core.glyphs;

import com.glyph.api.glyphs.GlyphTitle;
import java.util.Optional;

/** Resolves display text for shop and achievement title unlock ids. */
public final class GlyphTitles {

    /** Tab / nametag fallback. Not an unlock and not synced to Discord. */
    public static final String DEFAULT_DISPLAY = "Peasant";

    private GlyphTitles() {
    }

    public static Optional<String> displayText(String unlockId) {
        return GlyphTitle.fromId(unlockId).map(GlyphTitle::displayName);
    }

    public static String visibleText(String unlockId) {
        return displayText(unlockId).orElse(DEFAULT_DISPLAY);
    }

    public static boolean isTitleUnlock(String unlockId) {
        return displayText(unlockId).isPresent();
    }
}
