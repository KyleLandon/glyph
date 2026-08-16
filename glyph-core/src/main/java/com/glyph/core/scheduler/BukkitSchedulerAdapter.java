package com.glyph.core.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Single-thread Paper/Bukkit fallback when Folia region schedulers are absent.
 */
public final class BukkitSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;
    private final BukkitScheduler bukkit;

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.bukkit = plugin.getServer().getScheduler();
    }

    @Override
    public void runGlobal(Runnable task) {
        bukkit.runTask(plugin, task);
    }

    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        bukkit.runTaskLater(plugin, task, Math.max(1, delayTicks));
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        bukkit.runTask(plugin, task);
    }

    @Override
    public void runForEntity(Entity entity, Runnable task, Runnable retired) {
        if (entity.isDead() || !entity.isValid()) {
            if (retired != null) {
                retired.run();
            }
            return;
        }
        bukkit.runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        bukkit.runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runAsyncLater(Runnable task, Duration delay) {
        long ticks = Math.max(1, delay.toMillis() / 50);
        bukkit.runTaskLaterAsynchronously(plugin, task, ticks);
    }

    @Override
    public Executor async() {
        return this::runAsync;
    }
}
