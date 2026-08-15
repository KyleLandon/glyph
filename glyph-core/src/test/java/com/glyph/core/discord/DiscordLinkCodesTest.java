package com.glyph.core.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordLinkCodesTest {

    @Test
    void generatesGlyphPrefixedCodes() {
        String code = DiscordLinkCodes.generate();
        assertThat(code).matches("GLYPH-[A-Z2-9]{6}");
        assertThat(code).doesNotContain("0", "O", "1", "I");
    }

    @Test
    void normalizesInput() {
        assertThat(DiscordLinkCodes.normalize("  glyph-ab12cd  ")).isEqualTo("GLYPH-AB12CD");
    }
}
