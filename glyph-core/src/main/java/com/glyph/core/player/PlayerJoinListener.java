package com.glyph.core.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slf4j.Logger;

/**
 * Bridges {@link PlayerJoinEvent} to {@link PlayerService#handleJoin}.
 *
 * <p>Runs at MONITOR priority (identity persistence must not interfere with
 * plugins that cancel or modify the join) and swallows every exception: an
 * identity bug must never break a login.</p>
 */
public final class PlayerJoinListener implements Listener {

    private final PlayerService playerService;
    private final Logger logger;

    public PlayerJoinListener(PlayerService playerService, Logger logger) {
        this.playerService = playerService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            playerService.handleJoin(player.getUniqueId(), player.getName());
        } catch (Exception e) {
            logger.error("Unexpected error handling join of {}", player.getName(), e);
        }
    }
}
