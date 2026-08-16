package com.glyph.core.glyphs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/** Applies equipped Glyph cosmetics to player display names. */
public final class GlyphDisplay {

    private GlyphDisplay() {
    }

    public static NamedTextColor parseColor(String colorName) {
        if (colorName == null || colorName.isBlank()) {
            return NamedTextColor.WHITE;
        }
        NamedTextColor parsed = NamedTextColor.NAMES.value(colorName.toLowerCase().replace(' ', '_'));
        return parsed == null ? NamedTextColor.WHITE : parsed;
    }

    public static void applyDisplayName(Player player, NamedTextColor color, String titleText) {
        applyDisplayName(player, color, titleText, player.getName());
    }

    public static void applyDisplayName(
            Player player, NamedTextColor color, String titleText, String visibleName) {
        NamedTextColor effective = color == null ? NamedTextColor.WHITE : color;
        String shown = (visibleName == null || visibleName.isBlank()) ? player.getName() : visibleName;
        Component name = Component.text(shown, effective);
        if (titleText != null && !titleText.isBlank()) {
            player.displayName(Component.text("[", NamedTextColor.GRAY)
                    .append(Component.text(titleText, NamedTextColor.GRAY))
                    .append(Component.text("] ", NamedTextColor.GRAY))
                    .append(name));
        } else {
            player.displayName(name);
        }
    }
}
