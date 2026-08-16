package com.glyph.core.smp;

import com.glyph.core.config.SmpSettings;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.Plugin;

/** Forever World gamerules: one player can skip the night. */
public final class SmpWorldListener implements Listener {

    private final Plugin plugin;
    private final SmpSettings settings;

    public SmpWorldListener(Plugin plugin, SmpSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        for (World world : plugin.getServer().getWorlds()) {
            apply(world);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private void apply(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        if (settings.onePlayerSleep()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            GameRule sleep = GameRule.getByName("playersSleepingPercentage");
            if (sleep != null) {
                world.setGameRule(sleep, 1);
            }
        }
    }
}
