package com.glyph.core;

import com.glyph.api.GlyphApiProvider;
import com.glyph.api.economy.Money;
import com.glyph.core.api.GlyphApiImpl;
import com.glyph.core.auction.AuctionService;
import com.glyph.core.auction.PostgresAuctionRepository;
import com.glyph.core.auction.command.AhCommand;
import com.glyph.core.auction.command.ClaimCommand;
import com.glyph.core.auction.gui.AuctionGui;
import com.glyph.core.bounty.BountyService;
import com.glyph.core.bounty.CombatListener;
import com.glyph.core.bounty.PostgresBountyRepository;
import com.glyph.core.bounty.command.BountyCommand;
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
import com.glyph.core.delivery.DeliveryClaimer;
import com.glyph.core.delivery.DeliveryJoinNotifier;
import com.glyph.core.delivery.DeliveryService;
import com.glyph.core.delivery.PostgresDeliveryRepository;
import com.glyph.core.health.HealthService;
import com.glyph.core.hud.MoneyHud;
import com.glyph.core.hud.TabListDisplay;
import com.glyph.core.player.PlayerJoinListener;
import com.glyph.core.player.PlayerQuitListener;
import com.glyph.core.player.PlayerService;
import com.glyph.core.player.PlayerSessionService;
import com.glyph.core.player.PostgresPlayerRepository;
import com.glyph.core.player.WelcomeListener;
import com.glyph.core.redis.RedisManager;
import com.glyph.core.rewards.ActivityTracker;
import com.glyph.core.rewards.PlaytimeRewardService;
import com.glyph.core.scheduler.FoliaSchedulerAdapter;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.stats.PostgresStatsRepository;
import com.glyph.core.stats.StatType;
import com.glyph.core.stats.StatsListener;
import com.glyph.core.stats.StatsService;
import com.glyph.core.stats.command.PlaytimeCommand;
import com.glyph.core.stats.command.StatsCommand;
import com.glyph.core.stats.command.TopCommand;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
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
    private AuctionService auctionService;
    private DeliveryService deliveryService;
    private BountyService bountyService;
    private StatsService statsService;
    private PlaytimeRewardService playtimeRewardService;
    private final AtomicBoolean sweeperRunning = new AtomicBoolean();

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
                databaseManager::dataSource, settings.economy().startingBalance());
        this.playerService = new PlayerService(
                playerRepository,
                playerSessionService,
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        getServer().getPluginManager().registerEvents(
                new PlayerQuitListener(playerService, getSLF4JLogger()), this);

        PostgresEconomyRepository economyRepository = new PostgresEconomyRepository(
                databaseManager::dataSource, settings.economy().startingBalance());
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

        MoneyHud moneyHud = new MoneyHud(schedulerAdapter, settings.economy());
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

        // Auction house + delivery queue (GDD Phase 4, sections 21-23).
        this.auctionService = new AuctionService(
                new PostgresAuctionRepository(databaseManager::dataSource),
                settings.auction(),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        this.deliveryService = new DeliveryService(
                new PostgresDeliveryRepository(databaseManager::dataSource),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        DeliveryClaimer deliveryClaimer = new DeliveryClaimer(
                deliveryService, schedulerAdapter, getSLF4JLogger());
        AuctionGui auctionGui = new AuctionGui(
                auctionService, deliveryClaimer, schedulerAdapter,
                settings.economy(), getSLF4JLogger());
        getServer().getPluginManager().registerEvents(auctionGui, this);
        getServer().getPluginManager().registerEvents(
                new DeliveryJoinNotifier(deliveryService, schedulerAdapter), this);
        AhCommand ahCommand = new AhCommand(
                auctionService, auctionGui, deliveryService, schedulerAdapter, settings.economy());
        registerCommand("ah", ahCommand, ahCommand);
        registerCommand("claim", new ClaimCommand(deliveryClaimer), null);
        startExpirySweeper();

        // Buffered statistics (GDD Phase 6, sections 30, 104) + the activity
        // tracker feeding playtime rewards.
        this.statsService = new StatsService(
                new PostgresStatsRepository(databaseManager::dataSource),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        ActivityTracker activityTracker = new ActivityTracker();
        getServer().getPluginManager().registerEvents(
                new StatsListener(statsService, activityTracker), this);
        auctionService.addPurchaseListener((buyer, seller) -> {
            statsService.increment(buyer, StatType.AUCTION_PURCHASES);
            statsService.increment(seller, StatType.AUCTION_SALES);
        });
        registerCommand("stats", new StatsCommand(
                statsService, playerService, schedulerAdapter), null);
        registerCommand("playtime", new PlaytimeCommand(playerService, schedulerAdapter), null);
        startStatsFlusher();

        // Tab list: money + deaths on each row, branded header/footer.
        TabListDisplay tabList = new TabListDisplay(
                schedulerAdapter,
                settings.tab(),
                settings.economy(),
                statsService,
                getSLF4JLogger());
        economyService.addBalanceListener(tabList::updateBalance);
        getServer().getPluginManager().registerEvents(tabList, this);

        WelcomeListener welcomeListener = new WelcomeListener(schedulerAdapter, settings.economy());
        // After account + starting-balance mint: HUD/tab money, death baseline, welcome.
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(playerService, (uuid, firstJoin) -> {
                    economyService.resyncBalance(uuid);
                    tabList.onJoinPersisted(uuid);
                    welcomeListener.onPersisted(uuid, firstJoin);
                }, getSLF4JLogger()),
                this);

        // Money faucet: active-playtime earnings (GDD section 16).
        this.playtimeRewardService = new PlaytimeRewardService(
                activityTracker,
                economyRepository,
                economyService,
                settings.rewards(),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        startPlaytimeRewards();

        // Bounties + kill log (GDD Phase 5, sections 25, 33).
        this.bountyService = new BountyService(
                new PostgresBountyRepository(databaseManager::dataSource),
                settings.bounties(),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        getServer().getPluginManager().registerEvents(
                new CombatListener(bountyService, statsService, schedulerAdapter,
                        settings.economy(), getSLF4JLogger()), this);
        BountyCommand bountyCommand = new BountyCommand(
                bountyService, playerService, schedulerAdapter, settings.economy());
        registerCommand("bounty", bountyCommand, bountyCommand);
        TopCommand topCommand = new TopCommand(
                economyService, statsService, playerService, bountyService,
                schedulerAdapter, settings.economy());
        registerCommand("top", topCommand, topCommand);

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

    /**
     * Expired listings return to their sellers within a minute (GDD 21).
     * Self-rescheduling async loop; stops when the plugin disables.
     */
    private void startExpirySweeper() {
        sweeperRunning.set(true);
        schedulerAdapter.runAsyncLater(this::sweepAndReschedule, Duration.ofMinutes(1));
    }

    private void sweepAndReschedule() {
        if (!sweeperRunning.get()) {
            return;
        }
        auctionService.sweepExpired();
        schedulerAdapter.runAsyncLater(this::sweepAndReschedule, Duration.ofMinutes(1));
    }

    /**
     * Playtime reward loop (GDD 16): every window, snapshot who is online on
     * the global thread, pay the active ones async, then notify them on
     * their entity threads.
     */
    private void startPlaytimeRewards() {
        if (!settings.rewards().enabled()) {
            return;
        }
        schedulerAdapter.runAsyncLater(this::payoutPlaytimeAndReschedule,
                Duration.ofMinutes(settings.rewards().intervalMinutes()));
    }

    private void payoutPlaytimeAndReschedule() {
        if (!sweeperRunning.get()) {
            return;
        }
        schedulerAdapter.runGlobal(() -> {
            List<UUID> online = getServer().getOnlinePlayers().stream()
                    .map(Player::getUniqueId)
                    .toList();
            playtimeRewardService.payoutWindow(online).thenAccept(paid -> {
                String formatted = Money.of(settings.rewards().amount())
                        .format(settings.economy().currencySymbol());
                for (UUID uuid : paid) {
                    Player player = getServer().getPlayer(uuid);
                    if (player != null) {
                        schedulerAdapter.runForEntity(player, () -> player.sendMessage(
                                Component.text("+" + formatted + " earned for staying active.",
                                        NamedTextColor.GREEN)), null);
                    }
                }
            });
        });
        schedulerAdapter.runAsyncLater(this::payoutPlaytimeAndReschedule,
                Duration.ofMinutes(settings.rewards().intervalMinutes()));
    }

    /** Periodic stats batch flush (GDD 104); final flush happens in onDisable. */
    private void startStatsFlusher() {
        schedulerAdapter.runAsyncLater(this::flushStatsAndReschedule, Duration.ofSeconds(60));
    }

    private void flushStatsAndReschedule() {
        if (!sweeperRunning.get()) {
            return;
        }
        statsService.flushAll();
        schedulerAdapter.runAsyncLater(this::flushStatsAndReschedule, Duration.ofSeconds(60));
    }

    @Override
    public void onDisable() {
        sweeperRunning.set(false);
        // Shutdown flush (GDD 104): buffered stat deltas must not be lost.
        if (statsService != null) {
            try {
                statsService.flushAll();
            } catch (Exception e) {
                getSLF4JLogger().error("Final stats flush failed", e);
            }
        }
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
