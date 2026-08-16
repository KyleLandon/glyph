package com.glyph.core.glyphs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlyphTitlesTest {

    @Test
    void visibleTextFallsBackToPeasant() {
        assertThat(GlyphTitles.visibleText(null)).isEqualTo("Peasant");
        assertThat(GlyphTitles.visibleText("")).isEqualTo("Peasant");
        assertThat(GlyphTitles.visibleText("title_hunter")).isEqualTo("Bounty Hunter");
    }

    @Test
    void peasantIsNotAnUnlock() {
        assertThat(GlyphTitles.isTitleUnlock("Peasant")).isFalse();
        assertThat(GlyphTitles.isTitleUnlock("title_peasant")).isFalse();
    }
}
