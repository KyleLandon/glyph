package com.glyph.proxy;

import com.glyph.proxy.access.DiscordWhitelistConfig;
import com.glyph.proxy.access.DiscordWhitelistListener;
import com.glyph.proxy.access.PlayerAccessRepository;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;

/**
 * GlyphProxy — proxy-side plugin for the Glyph network.
 *
 * <p>v1 adds optional Discord alpha whitelist ({@code GLYPH_DISCORD_WHITELIST}).
 * Queueing / maintenance routing remain future work (GDD section 37).</p>
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
    private HikariDataSource dataSource;

    @Inject
    public GlyphProxyPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        DiscordWhitelistConfig whitelist = DiscordWhitelistConfig.fromEnv();
        if (whitelist.enabled()) {
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl(whitelist.jdbcUrl());
            hikari.setUsername(whitelist.dbUser());
            hikari.setPassword(whitelist.dbPassword());
            hikari.setMaximumPoolSize(3);
            hikari.setPoolName("glyph-proxy-pg");
            this.dataSource = new HikariDataSource(hikari);
            PlayerAccessRepository access = new PlayerAccessRepository(() -> dataSource);
            proxy.getEventManager().register(
                    this, new DiscordWhitelistListener(whitelist, access, logger));
            logger.info("Discord whitelist ENABLED — alpha access required to join");
        } else {
            logger.info("Discord whitelist disabled (GLYPH_DISCORD_WHITELIST != true)");
        }
        logger.info("GlyphProxy initialized — {} backend server(s) registered",
                proxy.getAllServers().size());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
        logger.info("GlyphProxy shutting down");
    }
}
