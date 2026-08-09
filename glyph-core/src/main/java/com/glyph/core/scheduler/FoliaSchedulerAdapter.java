package com.glyph.core.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * {@link SchedulerAdapter} backed by Folia's region-aware schedulers.
 */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private final Plugin plugin;

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runGlobal(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void runGlobalLater(Runnable task, long delayTicks) {
        plugin.getServer().getGlobalRegionScheduler()
                .runDelayed(plugin, scheduled -> task.run(), Math.max(1, delayTicks));
    }

    @Override
    public void runAtLocation(Location location, Runnable task) {
        plugin.getServer().getRegionScheduler().execute(plugin, location, task);
    }

    @Override
    public void runForEntity(Entity entity, Runnable task, Runnable retired) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), retired);
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    @Override
    public void runAsyncLater(Runnable task, Duration delay) {
        plugin.getServer().getAsyncScheduler()
                .runDelayed(plugin, scheduled -> task.run(), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Executor async() {
        return this::runAsync;
    }
}
