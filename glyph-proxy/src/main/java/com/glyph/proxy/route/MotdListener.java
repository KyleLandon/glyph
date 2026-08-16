package com.glyph.proxy.route;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import java.net.InetSocketAddress;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Server-list MOTD follows the hostname the client pinged. */
public final class MotdListener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        String host = event.getConnection().getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");
        event.setPing(event.getPing().asBuilder()
                .description(MINI.deserialize(MotdCatalog.miniMessageForHost(host)))
                .build());
    }
}
