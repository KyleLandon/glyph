package com.glyph.core.scheduler;

import org.bukkit.plugin.Plugin;

/** Picks Folia region schedulers when present, otherwise Bukkit. */
public final class Schedulers {

    private Schedulers() {
    }

    public static SchedulerAdapter create(Plugin plugin) {
        try {
            plugin.getServer().getGlobalRegionScheduler();
            return new FoliaSchedulerAdapter(plugin);
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            return new BukkitSchedulerAdapter(plugin);
        }
    }
}
