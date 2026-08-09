package com.glyph.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/**
 * GlyphProxy — proxy-side plugin for the Glyph network.
 *
 * <p>Phase 1 scope: lifecycle and logging only. Later phases add network-wide
 * player counts, queueing, maintenance routing and lobby fallback
 * (GDD section 37).</p>
 *
 * <p>Velocity itself handles the security-critical parts declaratively:
 * modern forwarding with a shared secret is configured in
 * {@code velocity.toml}, never in code (GDD sections 37-38).</p>
 */
@Plugin(
        id = "glyph-proxy",
        name = "GlyphProxy",
        version = "0.1.0",
        description = "Proxy-side platform plugin for the Glyph anarchy-economy network.",
        authors = {"Glyph"}
)
public final class GlyphProxyPlugin {

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public GlyphProxyPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("GlyphProxy initialized — {} backend server(s) registered",
                proxy.getAllServers().size());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("GlyphProxy shutting down");
    }
}
