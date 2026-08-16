package com.glyph.core.home;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HomeNamesTest {

    @Test
    void blankBecomesDefault() {
        assertThat(HomeNames.normalize(null)).contains(HomeNames.DEFAULT);
        assertThat(HomeNames.normalize("  ")).contains(HomeNames.DEFAULT);
    }

    @Test
    void acceptsShortLowercaseNames() {
        assertThat(HomeNames.normalize("Base")).contains("base");
        assertThat(HomeNames.normalize("shop_2")).contains("shop_2");
    }

    @Test
    void rejectsSpacesAndSymbols() {
        assertThat(HomeNames.normalize("my home")).isEmpty();
        assertThat(HomeNames.normalize("ok!")).isEmpty();
        assertThat(HomeNames.normalize("thisnameiswaytoolong")).isEmpty();
    }
}
