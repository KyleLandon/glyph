package com.glyph.api.glyphs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlyphTitleTest {

    @Test
    void fromIdResolvesShopAndAchievementTitles() {
        assertThat(GlyphTitle.fromId("title_hunter")).hasValue(GlyphTitle.HUNTER);
        assertThat(GlyphTitle.fromId("TITLE_WARLORD")).hasValue(GlyphTitle.WARLORD);
        assertThat(GlyphTitle.fromId("title_blooded")).hasValue(GlyphTitle.BLOODED);
        assertThat(GlyphTitle.fromId("unknown")).isEmpty();
    }

    @Test
    void hunterDisplayNameIsBountyHunter() {
        assertThat(GlyphTitle.HUNTER.displayName()).isEqualTo("Bounty Hunter");
        assertThat(GlyphTitle.HUNTER.id()).isEqualTo("title_hunter");
    }
}
