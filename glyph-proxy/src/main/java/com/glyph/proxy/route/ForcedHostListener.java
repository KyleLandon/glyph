package com.glyph.proxy.route;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.net.InetSocketAddress;
import org.slf4j.Logger;

/** Logs the handshake host and sends smp/anarchy names to the right backend. */
public final class ForcedHostListener {

    private final ProxyServer proxy;
    private final Logger logger;

    public ForcedHostListener(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onChooseInitial(PlayerChooseInitialServerEvent event) {
        String host = event.getPlayer().getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");
        String chosen = event.getInitialServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("none");
        logger.info("{} handshake host={} initial={}",
                event.getPlayer().getUsername(), host, chosen);

        ForcedHostRouter.backendForHost(host).ifPresent(backend -> {
            RegisteredServer server = proxy.getServer(backend).orElse(null);
            if (server == null) {
                logger.warn("Forced host {} -> {} but that backend is not registered",
                        host, backend);
                return;
            }
            if (!backend.equals(chosen)) {
                logger.info("{} rerouted {} -> {}", event.getPlayer().getUsername(),
                        chosen, backend);
            }
            event.setInitialServer(server);
        });
    }
}
