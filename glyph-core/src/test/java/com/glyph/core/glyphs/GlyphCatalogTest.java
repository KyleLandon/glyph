package com.glyph.core.glyphs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlyphCatalogTest {

    @Test
    void catalogHasExpectedProductCount() {
        assertThat(GlyphCatalog.size()).isEqualTo(16);
    }

    @Test
    void nameGoldProductMatchesSpec() {
        GlyphProduct product = GlyphCatalog.find("name_gold").orElseThrow();
        assertThat(product.type()).isEqualTo(GlyphProductType.NAME_COLOR);
        assertThat(product.displayName()).isEqualTo("Gold");
        assertThat(product.cost()).isEqualTo(8L);
        assertThat(product.payload()).isEqualTo("GOLD");
    }

    @Test
    void titleWarlordProductMatchesSpec() {
        GlyphProduct product = GlyphCatalog.find("title_warlord").orElseThrow();
        assertThat(product.type()).isEqualTo(GlyphProductType.TITLE);
        assertThat(product.cost()).isEqualTo(25L);
        assertThat(product.payload()).isEqualTo("Warlord");
    }

    @Test
    void deathGlyphProductMatchesSpec() {
        GlyphProduct product = GlyphCatalog.find("death_glyph").orElseThrow();
        assertThat(product.type()).isEqualTo(GlyphProductType.DEATH_MESSAGE);
        assertThat(product.displayName()).isEqualTo("Glyph");
        assertThat(product.cost()).isEqualTo(15L);
        assertThat(product.payload()).isEqualTo("glyph");
    }

    @Test
    void allNameColorsMatchSpecCosts() {
        assertProduct("name_gray", 3);
        assertProduct("name_yellow", 4);
        assertProduct("name_green", 6);
        assertProduct("name_aqua", 6);
        assertProduct("name_blue", 6);
        assertProduct("name_gold", 8);
        assertProduct("name_light_purple", 10);
        assertProduct("name_red", 12);
        assertProduct("name_dark_purple", 15);
    }

    private static void assertProduct(String id, long cost) {
        assertThat(GlyphCatalog.find(id)).isPresent();
        assertThat(GlyphCatalog.find(id).orElseThrow().cost()).isEqualTo(cost);
    }
}
