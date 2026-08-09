package com.glyph.core.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.slf4j.Logger;

/**
 * Bridges {@link PlayerQuitEvent} to {@link PlayerService#handleQuit}.
 *
 * <p>GDD section 133: a disconnect must never generate an unhandled
 * exception, so everything is caught and logged here as the last line of
 * defense (the service itself is also non-throwing).</p>
 */
public final class PlayerQuitListener implements Listener {

    private final PlayerService playerService;
    private final Logger logger;

    public PlayerQuitListener(PlayerService playerService, Logger logger) {
        this.playerService = playerService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        try {
            playerService.handleQuit(player.getUniqueId(), player.getName());
        } catch (Exception e) {
            logger.error("Unexpected error handling quit of {}", player.getName(), e);
        }
    }
}
