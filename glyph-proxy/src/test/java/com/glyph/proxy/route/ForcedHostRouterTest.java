package com.glyph.proxy.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForcedHostRouterTest {

    @Test
    void smpNamesRouteToSmp() {
        assertThat(ForcedHostRouter.backendForHost("smp.glyphmc.net")).contains("smp");
        assertThat(ForcedHostRouter.backendForHost("SMP.glyphmc.net:25565")).contains("smp");
        assertThat(ForcedHostRouter.backendForHost("smp.glyphmc.net.")).contains("smp");
    }

    @Test
    void anarchyAndPlayRouteToAnarchy() {
        assertThat(ForcedHostRouter.backendForHost("anarchy.glyphmc.net")).contains("anarchy");
        assertThat(ForcedHostRouter.backendForHost("play.glyphmc.net")).contains("anarchy");
    }

    @Test
    void playitTunnelNameDoesNotGuess() {
        assertThat(ForcedHostRouter.backendForHost("atoms-simmering.tun.ply.gg")).isEmpty();
        assertThat(ForcedHostRouter.backendForHost("")).isEmpty();
        assertThat(ForcedHostRouter.backendForHost(null)).isEmpty();
    }
}
