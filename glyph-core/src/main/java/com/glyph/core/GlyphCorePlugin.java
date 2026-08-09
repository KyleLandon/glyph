package com.glyph.core;

import com.glyph.api.GlyphApiProvider;
import com.glyph.core.api.GlyphApiImpl;
import com.glyph.core.command.GlyphCommand;
import com.glyph.core.config.GlyphSettings;
import com.glyph.core.database.DatabaseManager;
import com.glyph.core.health.HealthService;
import com.glyph.core.redis.RedisManager;
import com.glyph.core.scheduler.FoliaSchedulerAdapter;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GlyphCore — platform foundation for the Glyph anarchy-economy network.
 *
 * <p>Phase 1 scope (GDD section 132): configuration, Folia-safe scheduling,
 * PostgreSQL pool + Flyway migrations, Redis, health checks, clean lifecycle.
 * No gameplay features yet.</p>
 */
public final class GlyphCorePlugin extends JavaPlugin {

    private ExecutorService ioExecutor;
    private GlyphSettings settings;
    private SchedulerAdapter schedulerAdapter;
    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private HealthService healthService;

    @Override
    public void onEnable() {
        this.settings = GlyphSettings.load(this);
        getSLF4JLogger().info("Configuration loaded: {}", settings.describe());

        // Virtual threads: cheap, safe for blocking I/O, never Minecraft tick threads.
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.schedulerAdapter = new FoliaSchedulerAdapter(this);
        this.databaseManager = new DatabaseManager(settings.database(), getSLF4JLogger(), ioExecutor);
        this.redisManager = new RedisManager(settings.redis(), getSLF4JLogger(), ioExecutor);
        this.healthService = new HealthService(List.of(databaseManager, redisManager));

        GlyphApiProvider.register(new GlyphApiImpl(settings.serverId(), healthService));

        PluginCommand glyphCommand = getCommand("glyph");
        if (glyphCommand != null) {
            GlyphCommand executor = new GlyphCommand(this);
            glyphCommand.setExecutor(executor);
            glyphCommand.setTabCompleter(executor);
        }

        // Infrastructure connects asynchronously; the enable thread is never blocked.
        databaseManager.initAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                getSLF4JLogger().error(
                        "PostgreSQL initialization failed — economy features will be unavailable "
                                + "until the database is reachable", error);
            }
        });
        redisManager.initAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                getSLF4JLogger().error(
                        "Redis initialization failed — caching/cross-server features degraded; "
                                + "gameplay continues (PostgreSQL remains authoritative)", error);
            }
        });

        getSLF4JLogger().info("GlyphCore {} enabled (server id: {})",
                getPluginMeta().getVersion(), settings.serverId());
    }

    @Override
    public void onDisable() {
        GlyphApiProvider.unregister();

        if (redisManager != null) {
            redisManager.close();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (ioExecutor != null) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        getSLF4JLogger().info("GlyphCore disabled cleanly");
    }

    public GlyphSettings settings() {
        return settings;
    }

    public SchedulerAdapter schedulerAdapter() {
        return schedulerAdapter;
    }

    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    public RedisManager redisManager() {
        return redisManager;
    }

    public HealthService healthService() {
        return healthService;
    }
}
