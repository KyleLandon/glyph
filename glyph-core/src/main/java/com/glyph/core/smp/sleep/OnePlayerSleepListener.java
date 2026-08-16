package com.glyph.core.smp.sleep;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.plugin.Plugin;

/** One sleeper is enough to skip the night on Forever World. */
public final class OnePlayerSleepListener implements Listener {

    private final Plugin plugin;

    public OnePlayerSleepListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBed(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }
        Player sleeper = event.getPlayer();
        World world = sleeper.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!sleeper.isOnline() || !sleeper.isSleeping()) {
                return;
            }
            world.setTime(0);
            world.setStorm(false);
            world.setThundering(false);
            for (Player player : world.getPlayers()) {
                if (player.isSleeping()) {
                    player.wakeup(false);
                }
            }
        }, 60L);
    }
}
