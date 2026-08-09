package com.glyph.core.stats;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds gameplay events into the stats buffer (GDD section 30). Every
 * handler only bumps in-memory counters — no I/O on event threads.
 */
public final class StatsListener implements Listener {

    private final StatsService stats;

    public StatsListener(StatsService stats) {
        this.stats = stats;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        stats.increment(event.getPlayer().getUniqueId(), StatType.BLOCKS_BROKEN);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        stats.increment(event.getPlayer().getUniqueId(), StatType.BLOCKS_PLACED);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        stats.increment(victim.getUniqueId(), StatType.DEATHS);
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            stats.increment(killer.getUniqueId(), StatType.KILLS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // player deaths are handled above
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            stats.increment(killer.getUniqueId(), StatType.MOB_KILLS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (!from.getWorld().equals(to.getWorld())) {
            return;
        }
        long cm = Math.round(from.distance(to) * 100.0);
        stats.increment(event.getPlayer().getUniqueId(), StatType.DISTANCE_CM, cm);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        stats.flushPlayerAsync(event.getPlayer().getUniqueId());
    }
}
