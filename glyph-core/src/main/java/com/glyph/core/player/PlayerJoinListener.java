package com.glyph.core.player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
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
 *
 * <p>After the join row/account (and starting-balance mint) commit, optional
 * {@code afterPersisted} runs with {@code (uuid, firstJoin)} so the money HUD
 * can resync and first-join welcome messages can fire — the grant does not go
 * through {@code EconomyService}, so listeners would otherwise miss it.</p>
 */
public final class PlayerJoinListener implements Listener {

    private final PlayerService playerService;
    private final BiConsumer<UUID, Boolean> afterPersisted;
    private final Logger logger;

    public PlayerJoinListener(
            PlayerService playerService, BiConsumer<UUID, Boolean> afterPersisted, Logger logger) {
        this.playerService = playerService;
        this.afterPersisted = Objects.requireNonNullElse(afterPersisted, (id, first) -> { });
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        try {
            playerService.handleJoin(uuid, player.getName())
                    .whenComplete((firstJoin, error) -> {
                        if (error != null) {
                            return;
                        }
                        try {
                            afterPersisted.accept(uuid, Boolean.TRUE.equals(firstJoin));
                        } catch (Exception e) {
                            logger.error("Post-join hook failed for {}", player.getName(), e);
                        }
                    });
        } catch (Exception e) {
            logger.error("Unexpected error handling join of {}", player.getName(), e);
        }
    }
}
