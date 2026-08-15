package com.glyph.proxy.access;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

/** Denies login when Discord whitelist is enabled and the player lacks alpha access. */
public final class DiscordWhitelistListener {

    private final DiscordWhitelistConfig config;
    private final PlayerAccessRepository access;
    private final Logger logger;

    public DiscordWhitelistListener(
            DiscordWhitelistConfig config, PlayerAccessRepository access, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.access = Objects.requireNonNull(access, "access");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!config.enabled()) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        try {
            if (access.hasAlphaAccess(uuid)) {
                return;
            }
            event.setResult(ResultedEvent.ComponentResult.denied(Component.text(
                    "Glyph is in closed alpha.\n"
                            + "1) Join Discord: " + config.inviteUrl() + "\n"
                            + "2) In-game: /linkdiscord then /link <code> in Discord\n"
                            + "3) Get the Glyph Alpha role",
                    NamedTextColor.RED)));
            logger.info("Denied {} — no Discord alpha access", uuid);
        } catch (Exception e) {
            logger.error("Whitelist check failed for {} — denying join", uuid, e);
            event.setResult(ResultedEvent.ComponentResult.denied(Component.text(
                    "Server access check failed. Try again shortly.",
                    NamedTextColor.RED)));
        }
    }
}
