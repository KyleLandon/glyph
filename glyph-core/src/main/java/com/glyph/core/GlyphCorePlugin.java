package com.glyph.core;

import com.glyph.api.GlyphApiProvider;
import com.glyph.core.api.GlyphApiImpl;
import com.glyph.core.command.GlyphCommand;
import com.glyph.core.config.GlyphSettings;
import com.glyph.core.database.DatabaseManager;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.economy.PostgresEconomyRepository;
import com.glyph.core.economy.command.BalanceCommand;
import com.glyph.core.economy.command.BalanceTopCommand;
import com.glyph.core.economy.command.EconomyAdminCommand;
import com.glyph.core.economy.command.MoneyCommand;
import com.glyph.core.economy.command.PayCommand;
import com.glyph.core.health.HealthService;
import com.glyph.core.hud.MoneyHud;
import com.glyph.core.player.PlayerJoinListener;
import com.glyph.core.player.PlayerQuitListener;
import com.glyph.core.player.PlayerService;
import com.glyph.core.player.PlayerSessionService;
import com.glyph.core.player.PostgresPlayerRepository;
import com.glyph.core.redis.RedisManager;
import com.glyph.core.scheduler.FoliaSchedulerAdapter;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GlyphCore — platform foundation for the Glyph anarchy-economy network.
 *
 * <p>Phase 1 scope (GDD section 132): configuration, Folia-safe scheduling,
 * PostgreSQL pool + Flyway migrations, Redis, health checks, clean lifecycle.
 * Phase 2 (sections 100/133): player identity — profiles persisted on
 * join/quit, economy account created on first join.</p>
 */
public final class GlyphCorePlugin extends JavaPlugin {

    private ExecutorService ioExecutor;
    private GlyphSettings settings;
    private SchedulerAdapter schedulerAdapter;
    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private HealthService healthService;
    private PlayerSessionService playerSessionService;
    private PlayerService playerService;
    private EconomyService economyService;

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

        this.playerSessionService = new PlayerSessionService();
        PostgresPlayerRepository playerRepository = new PostgresPlayerRepository(
                databaseManager::dataSource, settings.economy().startingBalanceMinor());
        this.playerService = new PlayerService(
                playerRepository,
                playerSessionService,
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(playerService, getSLF4JLogger()), this);
        getServer().getPluginManager().registerEvents(
                new PlayerQuitListener(playerService, getSLF4JLogger()), this);

        PostgresEconomyRepository economyRepository =
                new PostgresEconomyRepository(databaseManager::dataSource);
        this.economyService = new EconomyService(
                economyRepository,
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());

        // Vault bridge: only touch Vault classes when VaultUnlocked (plugin
        // name "Vault") is actually installed.
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            com.glyph.core.economy.vault.VaultBridgeRegistrar.register(
                    this, economyRepository, playerRepository);
        }

        MoneyHud moneyHud = new MoneyHud(
                schedulerAdapter, settings.economy(), economyService, getSLF4JLogger());
        economyService.addBalanceListener(moneyHud::updateBalance);
        getServer().getPluginManager().registerEvents(moneyHud, this);

        GlyphApiProvider.register(new GlyphApiImpl(
                settings.serverId(), healthService, playerService, economyService));

        PluginCommand glyphCommand = getCommand("glyph");
        if (glyphCommand != null) {
            GlyphCommand executor = new GlyphCommand(this);
            glyphCommand.setExecutor(executor);
            glyphCommand.setTabCompleter(executor);
        }
        registerCommand("balance", new BalanceCommand(
                economyService, playerService, schedulerAdapter, settings.economy()), null);
        registerCommand("pay", new PayCommand(
                economyService, playerService, schedulerAdapter, settings.economy()), null);
        registerCommand("baltop", new BalanceTopCommand(
                economyService, schedulerAdapter, settings.economy()), null);
        MoneyCommand moneyCommand = new MoneyCommand(
                economyService, schedulerAdapter, settings.economy());
        registerCommand("money", moneyCommand, moneyCommand);
        EconomyAdminCommand ecoCommand = new EconomyAdminCommand(
                economyService, playerService, schedulerAdapter, settings.economy());
        registerCommand("eco", ecoCommand, ecoCommand);

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
        getServer().getServicesManager().unregisterAll(this);

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

    public PlayerService playerService() {
        return playerService;
    }

    public EconomyService economyService() {
        return economyService;
    }

    private void registerCommand(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getSLF4JLogger().error("Command {} missing from plugin.yml", name);
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }

    public PlayerSessionService playerSessionService() {
        return playerSessionService;
    }
}
