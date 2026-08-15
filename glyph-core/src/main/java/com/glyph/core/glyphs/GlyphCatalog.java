package com.glyph.core.glyphs;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hard-coded Glyph shop catalog — IDs and costs match {@code docs/GLYPHS.md}.
 */
public final class GlyphCatalog {

    private static final Map<String, GlyphProduct> PRODUCTS = build();

    private GlyphCatalog() {
    }

    public static Optional<GlyphProduct> find(String id) {
        return Optional.ofNullable(PRODUCTS.get(id));
    }

    public static Collection<GlyphProduct> all() {
        return PRODUCTS.values();
    }

    public static int size() {
        return PRODUCTS.size();
    }

    private static Map<String, GlyphProduct> build() {
        Map<String, GlyphProduct> products = new LinkedHashMap<>();
        nameColor(products, "name_gray", "Gray", 3, "GRAY");
        nameColor(products, "name_yellow", "Yellow", 4, "YELLOW");
        nameColor(products, "name_green", "Green", 6, "GREEN");
        nameColor(products, "name_aqua", "Aqua", 6, "AQUA");
        nameColor(products, "name_blue", "Blue", 6, "BLUE");
        nameColor(products, "name_gold", "Gold", 8, "GOLD");
        nameColor(products, "name_light_purple", "Light purple", 10, "LIGHT_PURPLE");
        nameColor(products, "name_red", "Red", 12, "RED");
        nameColor(products, "name_dark_purple", "Dark purple", 15, "DARK_PURPLE");

        title(products, "title_wanderer", "Wanderer", 5, "Wanderer");
        title(products, "title_outlaw", "Outlaw", 8, "Outlaw");
        title(products, "title_warlord", "Warlord", 25, "Warlord");

        deathMessage(products, "death_fell", "Fell before", 5, "fell");
        deathMessage(products, "death_claimed", "Claimed", 8, "claimed");
        deathMessage(products, "death_silence", "Silence", 12, "silence");
        deathMessage(products, "death_glyph", "Glyph", 15, "glyph");
        return Map.copyOf(products);
    }

    private static void nameColor(
            Map<String, GlyphProduct> products, String id, String name, long cost, String color) {
        products.put(id, new GlyphProduct(id, GlyphProductType.NAME_COLOR, name, cost, color));
    }

    private static void title(
            Map<String, GlyphProduct> products, String id, String name, long cost, String titleText) {
        products.put(id, new GlyphProduct(id, GlyphProductType.TITLE, name, cost, titleText));
    }

    private static void deathMessage(
            Map<String, GlyphProduct> products, String id, String name, long cost, String styleId) {
        products.put(id, new GlyphProduct(id, GlyphProductType.DEATH_MESSAGE, name, cost, styleId));
    }
}
