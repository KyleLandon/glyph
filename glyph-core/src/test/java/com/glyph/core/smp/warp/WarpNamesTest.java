package com.glyph.core.smp.warp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WarpNamesTest {

    @Test
    void acceptsSimpleNames() {
        assertThat(WarpNames.normalize("Shop")).contains("shop");
        assertThat(WarpNames.normalize("east_gate")).contains("east_gate");
    }

    @Test
    void rejectsReservedAndJunk() {
        assertThat(WarpNames.normalize("spawn")).isEmpty();
        assertThat(WarpNames.normalize("set")).isEmpty();
        assertThat(WarpNames.normalize("list")).isEmpty();
        assertThat(WarpNames.normalize("bad name")).isEmpty();
        assertThat(WarpNames.normalize("")).isEmpty();
        assertThat(WarpNames.normalize(null)).isEmpty();
    }
}
