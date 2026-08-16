package com.glyph.core;

import com.glyph.api.GlyphApiProvider;
import com.glyph.api.economy.Money;
import com.glyph.core.api.GlyphApiImpl;
import com.glyph.core.auction.AuctionService;
import com.glyph.core.auction.PostgresAuctionRepository;
import com.glyph.core.auction.command.AhCommand;
import com.glyph.core.auction.gui.AuctionGui;
import com.glyph.core.bounty.BountyService;
import com.glyph.core.bounty.CombatListener;
import com.glyph.core.bounty.PostgresBountyRepository;
import com.glyph.core.bounty.command.BountyCommand;
import com.glyph.core.bounty.gui.WantedBoardGui;
import com.glyph.core.chat.ItemChatListener;
import com.glyph.core.chat.LocalChatListener;
import com.glyph.core.chat.command.GlobalChatCommand;
import com.glyph.core.chat.command.ItemCommand;
import com.glyph.core.chat.command.LocalChatCommand;
import com.glyph.core.command.AnarchyOnlyCommand;
import com.glyph.core.command.SmpOnlyCommand;
import com.glyph.core.command.GlyphCommand;
import com.glyph.core.command.CommandTabs;
import com.glyph.core.command.RestartCommand;
import com.glyph.core.config.GlyphSettings;
import com.glyph.core.database.DatabaseManager;
import com.glyph.core.discord.DiscordLinkService;
import com.glyph.core.discord.PostgresDiscordLinkRepository;
import com.glyph.core.discord.command.LinkDiscordCommand;
import com.glyph.core.discord.command.UnlinkDiscordCommand;
import com.glyph.core.event.GlyphEventPublisher;
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
import com.glyph.core.home.HomeService;
import com.glyph.core.home.PostgresHomeRepository;
import com.glyph.core.home.command.HomeCommand;
import com.glyph.core.nick.NicknameService;
import com.glyph.core.nick.PostgresNicknameRepository;
import com.glyph.core.nick.command.MeCommand;
import com.glyph.core.nick.command.NicknameCommand;
import com.glyph.core.health.HealthService;
import com.glyph.core.glyphs.DeathMessageListener;
import com.glyph.core.glyphs.GlyphAchievementService;
import com.glyph.core.glyphs.GlyphShopService;
import com.glyph.core.glyphs.GlyphsService;
import com.glyph.core.glyphs.PostgresGlyphsRepository;
import com.glyph.core.glyphs.command.GlyphAdminCommand;
import com.glyph.core.glyphs.command.GlyphsCommand;
import com.glyph.core.hud.MoneyHud;
import com.glyph.core.hud.TabListDisplay;
import com.glyph.core.player.PlayerJoinListener;
import com.glyph.core.player.PlayerQuitListener;
import com.glyph.core.player.PlayerService;
import com.glyph.core.player.PlayerSessionService;
import com.glyph.core.player.PostgresPlayerRepository;
import com.glyph.core.player.StarterKitService;
import com.glyph.core.player.WelcomeListener;
import com.glyph.core.player.command.RulesCommand;
import com.glyph.core.player.command.StarterCommand;
import com.glyph.core.redis.RedisManager;
import com.glyph.core.rewards.ActivityTracker;
import com.glyph.core.rewards.PlaytimeRewardService;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.scheduler.Schedulers;
import com.glyph.core.stats.PostgresStatsRepository;
import com.glyph.core.stats.StatType;
import com.glyph.core.stats.StatsListener;
import com.glyph.core.stats.StatsService;
import com.glyph.core.stats.command.PlaytimeCommand;
import com.glyph.core.stats.command.StatsCommand;
import com.glyph.core.stats.command.TopCommand;
import com.glyph.core.smp.SmpWorldListener;
import com.glyph.core.smp.armorstand.ArmorStandEditor;
import com.glyph.core.smp.command.ClaimBlocksCommand;
import com.glyph.core.smp.command.MapImageCommand;
import com.glyph.core.smp.command.ShopCommand;
import com.glyph.core.smp.command.SitCommand;
import com.glyph.core.smp.command.SpawnCommand;
import com.glyph.core.smp.command.TpaCommand;
import com.glyph.core.smp.command.TradeCommand;
import com.glyph.core.smp.command.WarpCommand;
import com.glyph.core.smp.command.WildCommand;
import com.glyph.core.smp.imagemap.ImageMapService;
import com.glyph.core.smp.shop.ChestShopService;
import com.glyph.core.smp.shop.PostgresChestShopRepository;
import com.glyph.core.smp.shop.ShopListener;
import com.glyph.core.smp.sit.SitService;
import com.glyph.core.smp.sleep.OnePlayerSleepListener;
import com.glyph.core.smp.tpa.TpaService;
import com.glyph.core.smp.trade.TradeGui;
import com.glyph.core.smp.warp.PostgresWarpRepository;
import com.glyph.core.smp.warp.WarpService;
import com.glyph.core.world.CreeperGriefListener;
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
    private GlyphsService glyphsService;
    private StatsService statsService;
    private PlaytimeRewardService playtimeRewardService;
    private SitService sitService;
    private final AtomicBoolean sweeperRunning = new AtomicBoolean();

    @Override
    public void onEnable() {
        this.settings = GlyphSettings.load(this);
        getSLF4JLogger().info("Configuration loaded: {}", settings.describe());

        // Virtual threads: cheap, safe for blocking I/O, never Minecraft tick threads.
        this.ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.schedulerAdapter = Schedulers.create(this);
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

        GlyphEventPublisher eventPublisher = new GlyphEventPublisher(redisManager);

        PostgresGlyphsRepository glyphsRepository = new PostgresGlyphsRepository(
                databaseManager::dataSource);
        GlyphAchievementService glyphAchievements = new GlyphAchievementService(
                glyphsRepository,
                settings.glyphs(),
                schedulerAdapter,
                eventPublisher,
                getSLF4JLogger());
        this.glyphsService = new GlyphsService(
                glyphsRepository,
                glyphAchievements,
                settings.glyphs(),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger(),
                eventPublisher);

        DiscordLinkService discordLinkService = new DiscordLinkService(
                new PostgresDiscordLinkRepository(databaseManager::dataSource),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        registerCommand("linkdiscord", new LinkDiscordCommand(
                discordLinkService, settings.discord(), schedulerAdapter), null);
        registerCommand("unlinkdiscord", new UnlinkDiscordCommand(
                discordLinkService, schedulerAdapter), null);

        getServer().getPluginManager().registerEvents(
                new LocalChatListener(settings.chat()), this);
        getServer().getPluginManager().registerEvents(
                new ItemChatListener(settings.chat(), schedulerAdapter), this);
        registerCommand("item", new ItemCommand(settings.chat()), null);
        GlobalChatCommand globalChat = new GlobalChatCommand();
        LocalChatCommand localChat = new LocalChatCommand(settings.chat());
        registerCommand("g", globalChat, globalChat);
        registerCommand("l", localChat, localChat);

        MoneyHud moneyHud = new MoneyHud(
                schedulerAdapter, settings.economy(), settings.glyphs(), glyphsService);
        economyService.addBalanceListener(moneyHud::updateBalance);
        getServer().getPluginManager().registerEvents(moneyHud, this);

        GlyphShopService glyphShopService = new GlyphShopService(
                glyphsService, getSLF4JLogger());
        glyphsService.addBalanceListener(moneyHud::updateGlyphs);
        glyphsService.addHudListener(moneyHud::onHudPreferenceChanged);
        GlyphsCommand glyphsCommand = new GlyphsCommand(
                glyphsService, glyphShopService, schedulerAdapter);
        registerCommand("glyphs", glyphsCommand, glyphsCommand);
        GlyphAdminCommand glyphAdminCommand = new GlyphAdminCommand(
                glyphsService, discordLinkService, playerService, schedulerAdapter);
        registerCommand("glyphadmin", glyphAdminCommand, glyphAdminCommand);
        getServer().getPluginManager().registerEvents(
                new DeathMessageListener(glyphsService), this);

        GlyphApiProvider.register(new GlyphApiImpl(
                settings.serverId(), healthService, playerService, economyService));

        RestartCommand restartCommand = new RestartCommand(schedulerAdapter, getSLF4JLogger());
        registerCommand("restart", restartCommand, restartCommand);
        PluginCommand glyphCommand = getCommand("glyph");
        if (glyphCommand != null) {
            GlyphCommand executor = new GlyphCommand(this, restartCommand);
            glyphCommand.setExecutor(executor);
            glyphCommand.setTabCompleter(executor);
        }
        BalanceCommand balanceCommand = new BalanceCommand(
                economyService, playerService, schedulerAdapter, settings.economy());
        registerCommand("balance", balanceCommand, balanceCommand);
        PayCommand payCommand = new PayCommand(
                economyService, playerService, schedulerAdapter, settings.economy());
        registerCommand("pay", payCommand, payCommand);
        registerCommand("baltop", new BalanceTopCommand(
                economyService, schedulerAdapter, settings.economy()), null);
        MoneyCommand moneyCommand = new MoneyCommand(
                economyService, schedulerAdapter, settings.economy());
        registerCommand("money", moneyCommand, moneyCommand);
        EconomyAdminCommand ecoCommand = new EconomyAdminCommand(
                economyService, playerService, schedulerAdapter, settings.economy());
        registerCommand("eco", ecoCommand, ecoCommand);

        // Auction house + delivery queue (GDD Phase 4, sections 21-23).
        // Each backend has its own market; items never cross (ADR-013).
        if (settings.auction().enabled()) {
            String market = settings.role().marketId();
            this.auctionService = new AuctionService(
                    new PostgresAuctionRepository(databaseManager::dataSource, market),
                    settings.auction(),
                    databaseManager::isReady,
                    ioExecutor,
                    getSLF4JLogger());
            this.deliveryService = new DeliveryService(
                    new PostgresDeliveryRepository(databaseManager::dataSource, market),
                    databaseManager::isReady,
                    ioExecutor,
                    getSLF4JLogger());
            DeliveryClaimer deliveryClaimer = new DeliveryClaimer(
                    deliveryService, schedulerAdapter, getSLF4JLogger());
            AuctionGui auctionGui = new AuctionGui(
                    auctionService, deliveryClaimer, schedulerAdapter,
                    settings.economy(), getSLF4JLogger(),
                    settings.role().isSmp() ? "Forever Auction" : "Auction House");
            getServer().getPluginManager().registerEvents(auctionGui, this);
            getServer().getPluginManager().registerEvents(
                    new DeliveryJoinNotifier(deliveryService, schedulerAdapter), this);
            AhCommand ahCommand = new AhCommand(
                    auctionService, auctionGui, deliveryService, deliveryClaimer,
                    schedulerAdapter, settings.economy(), economyService);
            registerCommand("ah", ahCommand, ahCommand);
            startExpirySweeper();
        } else {
            registerCommand("ah", new AnarchyOnlyCommand("The auction house"), null);
        }

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
        if (auctionService != null) {
            auctionService.addPurchaseListener(sale -> {
                statsService.increment(sale.buyerUuid(), StatType.AUCTION_PURCHASES);
                statsService.increment(sale.sellerUuid(), StatType.AUCTION_SALES);
                glyphsService.noteAuctionSale(sale.sellerUuid(), sale.priceDollars());
                economyService.resyncBalance(sale.buyerUuid());
                economyService.resyncBalance(sale.sellerUuid());
            });
        }
        StatsCommand statsCommand = new StatsCommand(
                statsService, playerService, schedulerAdapter);
        registerCommand("stats", statsCommand, statsCommand);
        registerCommand("playtime", new PlaytimeCommand(playerService, schedulerAdapter), null);
        startStatsFlusher();

        // Tab list: money + deaths on each row, branded header/footer.
        TabListDisplay tabList = new TabListDisplay(
                schedulerAdapter,
                settings.tab(),
                settings.economy(),
                settings.glyphs(),
                glyphsService,
                statsService,
                getSLF4JLogger());
        economyService.addBalanceListener(tabList::updateBalance);
        glyphsService.addBalanceListener(tabList::updateGlyphs);
        glyphsService.addColorListener(tabList::onColorChanged);
        glyphsService.addTitleListener(tabList::onTitleChanged);
        getServer().getPluginManager().registerEvents(tabList, this);

        final NicknameService nicknameService;
        final ChestShopService chestShops;
        if (settings.role().isSmp()) {
            HomeCommand homeCommand = new HomeCommand(
                    new HomeService(
                            new PostgresHomeRepository(
                                    databaseManager::dataSource, settings.role().marketId()),
                            databaseManager::isReady,
                            getSLF4JLogger()),
                    schedulerAdapter);
            registerCommand("home", homeCommand, homeCommand);
            registerCommand("sethome", homeCommand, homeCommand);
            registerCommand("delhome", homeCommand, homeCommand);
            registerCommand("homes", homeCommand, homeCommand);

            nicknameService = new NicknameService(
                    new PostgresNicknameRepository(
                            databaseManager::dataSource, settings.role().marketId()),
                    databaseManager::isReady,
                    name -> {
                        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
                            if (online.getName().equalsIgnoreCase(name)) {
                                return java.util.Optional.of(online.getUniqueId());
                            }
                        }
                        return java.util.Optional.empty();
                    },
                    getSLF4JLogger());
            glyphsService.setVisibleNameLookup(
                    uuid -> nicknameService.visibleName(uuid, ""));
            tabList.setVisibleNameLookup(
                    uuid -> nicknameService.visibleName(uuid, ""));
            NicknameCommand nicknameCommand = new NicknameCommand(
                    nicknameService, glyphsService, tabList, schedulerAdapter);
            registerCommand("nickname", nicknameCommand, nicknameCommand);
            registerCommand("me", new MeCommand(nicknameService, settings.chat()), null);
            getServer().getPluginManager().registerEvents(new CreeperGriefListener(), this);
            getServer().getPluginManager().registerEvents(
                    new SmpWorldListener(this, settings.smp()), this);
            if (settings.smp().onePlayerSleep()) {
                getServer().getPluginManager().registerEvents(
                        new OnePlayerSleepListener(this), this);
            }

            registerCommand("spawn", new SpawnCommand(schedulerAdapter), null);
            registerCommand("wild", new WildCommand(settings.smp(), schedulerAdapter), null);

            TpaCommand tpaCommand = new TpaCommand(
                    new TpaService(), settings.smp(), schedulerAdapter);
            getServer().getPluginManager().registerEvents(tpaCommand, this);
            registerCommand("tpa", tpaCommand, tpaCommand);
            registerCommand("tpahere", tpaCommand, tpaCommand);
            registerCommand("tpaccept", tpaCommand, tpaCommand);
            registerCommand("tpdeny", tpaCommand, tpaCommand);

            this.sitService = new SitService();
            if (settings.smp().sitEnabled()) {
                getServer().getPluginManager().registerEvents(sitService, this);
                registerCommand("sit", new SitCommand(sitService), null);
            } else {
                registerCommand("sit", new SmpOnlyCommand("Sitting"), null);
            }

            ClaimBlocksCommand claimBlocks = new ClaimBlocksCommand(
                    economyService, settings.smp(), settings.economy(), schedulerAdapter);
            registerCommand("claimblocks", claimBlocks, claimBlocks);

            WarpService warpService = new WarpService(
                    new PostgresWarpRepository(
                            databaseManager::dataSource, settings.role().marketId()),
                    databaseManager::isReady,
                    settings.smp().maxWarpsPerPlayer(),
                    getSLF4JLogger());
            WarpCommand warpCommand = new WarpCommand(
                    warpService, economyService, settings.smp(), settings.economy(),
                    schedulerAdapter);
            registerCommand("warp", warpCommand, warpCommand);
            registerCommand("warps", warpCommand, warpCommand);

            chestShops = new ChestShopService(
                    new PostgresChestShopRepository(
                            databaseManager::dataSource, settings.role().marketId()),
                    databaseManager::isReady,
                    getSLF4JLogger());
            if (settings.smp().shopsEnabled()) {
                ShopCommand shopCommand = new ShopCommand(
                        chestShops, settings.economy(), schedulerAdapter);
                registerCommand("shop", shopCommand, shopCommand);
                getServer().getPluginManager().registerEvents(
                        new ShopListener(chestShops, economyService, settings.economy(),
                                schedulerAdapter, getSLF4JLogger()), this);
            } else {
                registerCommand("shop", new SmpOnlyCommand("Chest shops"), null);
            }

            if (settings.smp().tradeEnabled()) {
                TradeGui tradeGui = new TradeGui(
                        economyService, settings.economy(), schedulerAdapter);
                getServer().getPluginManager().registerEvents(tradeGui, this);
                TradeCommand tradeCommand = new TradeCommand(tradeGui);
                registerCommand("trade", tradeCommand, tradeCommand);
            } else {
                registerCommand("trade", new SmpOnlyCommand("Trading"), null);
            }

            getServer().getPluginManager().registerEvents(new ArmorStandEditor(), this);

            if (settings.smp().imageMapsEnabled()) {
                MapImageCommand mapImage = new MapImageCommand(new ImageMapService(schedulerAdapter));
                registerCommand("mapimage", mapImage, mapImage);
            } else {
                registerCommand("mapimage", new SmpOnlyCommand("Image maps"), null);
            }
        } else {
            nicknameService = null;
            chestShops = null;
            CommandExecutor noHomes = new SmpOnlyCommand("Homes");
            registerCommand("home", noHomes, null);
            registerCommand("sethome", noHomes, null);
            registerCommand("delhome", noHomes, null);
            registerCommand("homes", noHomes, null);
            CommandExecutor noNicks = new SmpOnlyCommand("Nicknames");
            registerCommand("nickname", noNicks, null);
            registerCommand("me", new SmpOnlyCommand("Emotes"), null);
            CommandExecutor forever = new SmpOnlyCommand("This");
            registerCommand("spawn", forever, null);
            registerCommand("wild", forever, null);
            registerCommand("tpa", forever, null);
            registerCommand("tpahere", forever, null);
            registerCommand("tpaccept", forever, null);
            registerCommand("tpdeny", forever, null);
            registerCommand("sit", forever, null);
            registerCommand("claimblocks", forever, null);
            registerCommand("warp", forever, null);
            registerCommand("warps", forever, null);
            registerCommand("shop", forever, null);
            registerCommand("trade", forever, null);
            registerCommand("mapimage", forever, null);
        }

        StarterKitService starterKit = new StarterKitService(this, settings.starter(), settings.role());
        WelcomeListener welcomeListener = new WelcomeListener(
                schedulerAdapter, settings.economy(), starterKit, settings.role());
        registerCommand("rules", new RulesCommand(settings.role()), null);
        StarterCommand starterCommand = new StarterCommand(starterKit, schedulerAdapter);
        registerCommand("starter", starterCommand, starterCommand);
        // After account + starting-balance mint: HUD/tab money, death baseline, welcome.
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(playerService, (uuid, firstJoin) -> {
                    if (nicknameService != null) {
                        nicknameService.load(uuid);
                    }
                    economyService.resyncBalance(uuid);
                    glyphsService.resyncOnJoin(uuid);
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

        // Bounties + kill log (GDD Phase 5). Service stays up so /top bounty
        // works on SMP; combat and /bounty are anarchy-only.
        this.bountyService = new BountyService(
                new PostgresBountyRepository(databaseManager::dataSource),
                settings.bounties(),
                databaseManager::isReady,
                ioExecutor,
                getSLF4JLogger());
        if (settings.bounties().enabled()) {
            getServer().getPluginManager().registerEvents(
                    new CombatListener(bountyService, statsService, glyphsService, schedulerAdapter,
                            settings.economy(), economyService, getSLF4JLogger()), this);
            WantedBoardGui wantedBoard = new WantedBoardGui(
                    bountyService, schedulerAdapter, settings.economy(), getSLF4JLogger());
            getServer().getPluginManager().registerEvents(wantedBoard, this);
            BountyCommand bountyCommand = new BountyCommand(
                    bountyService, playerService, schedulerAdapter, settings.economy(),
                    wantedBoard, economyService);
            registerCommand("bounty", bountyCommand, bountyCommand);
        } else {
            registerCommand("bounty", new AnarchyOnlyCommand("Bounties"), null);
        }
        TopCommand topCommand = new TopCommand(
                economyService, statsService, playerService, bountyService,
                schedulerAdapter, settings.economy());
        registerCommand("top", topCommand, topCommand);

        sweeperRunning.set(true);

        // Infrastructure connects asynchronously; the enable thread is never blocked.
        databaseManager.initAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                getSLF4JLogger().error(
                        "PostgreSQL initialization failed — economy features will be unavailable "
                                + "until the database is reachable", error);
                return;
            }
            if (chestShops != null) {
                chestShops.loadCache();
            }
        });
        redisManager.initAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                getSLF4JLogger().error(
                        "Redis initialization failed — caching/cross-server features degraded; "
                                + "gameplay continues (PostgreSQL remains authoritative)", error);
            }
        });

        getSLF4JLogger().info("GlyphCore {} enabled (server id: {}, role: {})",
                getPluginMeta().getVersion(), settings.serverId(),
                settings.role().name().toLowerCase());
    }

    /**
     * Expired listings return to their sellers within a minute (GDD 21).
     * Self-rescheduling async loop; stops when the plugin disables.
     */
    private void startExpirySweeper() {
        sweeperRunning.set(true);
        if (auctionService == null) {
            return;
        }
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
        if (sitService != null) {
            sitService.standAll();
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
        command.setTabCompleter(completer != null ? completer : CommandTabs.NONE);
    }

    public PlayerSessionService playerSessionService() {
        return playerSessionService;
    }
}
