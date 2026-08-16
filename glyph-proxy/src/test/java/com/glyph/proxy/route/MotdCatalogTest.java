package com.glyph.proxy.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MotdCatalogTest {

    @Test
    void smpHostUsesSmpCopy() {
        assertThat(MotdCatalog.miniMessageForHost("smp.glyphmc.net")).isEqualTo(MotdCatalog.SMP);
        assertThat(MotdCatalog.SMP).contains("Forever World").contains("Hang out");
    }

    @Test
    void anarchyAndUnknownUseAnarchyCopy() {
        assertThat(MotdCatalog.miniMessageForHost("anarchy.glyphmc.net"))
                .isEqualTo(MotdCatalog.ANARCHY);
        assertThat(MotdCatalog.miniMessageForHost("play.glyphmc.net"))
                .isEqualTo(MotdCatalog.ANARCHY);
        assertThat(MotdCatalog.miniMessageForHost("localhost"))
                .isEqualTo(MotdCatalog.ANARCHY);
        assertThat(MotdCatalog.ANARCHY).contains("Anarchy").contains("No claims");
    }
}
