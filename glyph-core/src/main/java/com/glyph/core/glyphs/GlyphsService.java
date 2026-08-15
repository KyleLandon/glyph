package com.glyph.core.glyphs;

import com.glyph.api.discord.DiscordTier;
import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.core.config.GlyphCurrencySettings;
import com.glyph.core.event.GlyphEventPublisher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/**
 * Async Glyph balance and cosmetic orchestration — see {@code docs/GLYPHS.md}.
 */
public final class GlyphsService {

    private final GlyphsRepository repository;
    private final GlyphAchievementService achievements;
    private final GlyphCurrencySettings settings;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;
    private final GlyphEventPublisher eventPublisher;

    private final List<BiConsumer<UUID, Long>> balanceListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> colorListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> titleListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> deathStyleListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> hudListeners = new CopyOnWriteArrayList<>();

    private final Map<UUID, Long> balanceCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lifetimeEarnedCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<NamedTextColor>> nameColorCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> equippedTitleCache = new ConcurrentHashMap<>();
    private final Map<UUID, Optional<String>> deathStyleCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hudEnabledCache = new ConcurrentHashMap<>();

    public GlyphsService(
            GlyphsRepository repository,
            GlyphAchievementService achievements,
            GlyphCurrencySettings settings,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger,
            GlyphEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.achievements = Objects.requireNonNull(achievements, "achievements");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public GlyphCurrencySettings settings() {
        return settings;
    }

    public void addBalanceListener(BiConsumer<UUID, Long> listener) {
        balanceListeners.add(listener);
    }

    public void addColorListener(Consumer<UUID> listener) {
        colorListeners.add(listener);
    }

    public void addTitleListener(Consumer<UUID> listener) {
        titleListeners.add(listener);
    }

    public void addDeathStyleListener(Consumer<UUID> listener) {
        deathStyleListeners.add(listener);
    }

    public void addHudListener(Consumer<UUID> listener) {
        hudListeners.add(listener);
    }

    public CompletableFuture<Void> resyncOnJoin(UUID playerUuid) {
        if (!settings.enabled() || !databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            long balance = repository.balance(playerUuid);
            balanceCache.put(playerUuid, balance);
            notifyBalance(playerUuid, balance);

            long lifetimeEarned = repository.lifetimeEarned(playerUuid);
            lifetimeEarnedCache.put(playerUuid, lifetimeEarned);

            repository.nameColor(playerUuid).ifPresentOrElse(
                    color -> nameColorCache.put(playerUuid, Optional.of(GlyphDisplay.parseColor(color))),
                    () -> nameColorCache.put(playerUuid, Optional.empty()));

            equippedTitleCache.put(playerUuid, repository.equippedTitle(playerUuid));
            deathStyleCache.put(playerUuid, repository.deathStyle(playerUuid));
            hudEnabledCache.put(playerUuid, repository.hudEnabled(playerUuid));

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                applyDisplayName(player);
            }
            notifyColor(playerUuid);
            notifyTitle(playerUuid);
            notifyDeathStyle(playerUuid);
            notifyHud(playerUuid);
        }, ioExecutor).exceptionally(error -> {
            logger.debug("Glyph resync failed for {}", playerUuid, error);
            return null;
        });
    }

    public Optional<NamedTextColor> nameColor(UUID playerUuid) {
        return nameColorCache.getOrDefault(playerUuid, Optional.empty());
    }

    public Optional<String> equippedTitleId(UUID playerUuid) {
        return equippedTitleCache.getOrDefault(playerUuid, Optional.empty());
    }

    public Optional<String> equippedTitleText(UUID playerUuid) {
        return equippedTitleId(playerUuid).flatMap(GlyphTitles::displayText);
    }

    public Optional<String> deathStyleProductId(UUID playerUuid) {
        return deathStyleCache.getOrDefault(playerUuid, Optional.empty());
    }

    public boolean hudEnabled(UUID playerUuid) {
        return hudEnabledCache.getOrDefault(playerUuid, false);
    }

    public long lifetimeEarned(UUID playerUuid) {
        return lifetimeEarnedCache.getOrDefault(playerUuid, 0L);
    }

    public static Optional<String> discordTier(long lifetimeEarned) {
        return DiscordTier.forLifetimeEarned(lifetimeEarned).map(DiscordTier::displayName);
    }

    public void applyDisplayName(Player player) {
        Optional<NamedTextColor> color = nameColor(player.getUniqueId());
        Optional<String> title = equippedTitleText(player.getUniqueId());
        GlyphDisplay.applyDisplayName(player, color.orElse(NamedTextColor.WHITE), title.orElse(null));
    }

    public CompletableFuture<Long> balance(UUID playerUuid) {
        if (!settings.enabled() || !databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(0L);
        }
        Long cached = balanceCache.get(playerUuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            long balance = repository.balance(playerUuid);
            balanceCache.put(playerUuid, balance);
            return balance;
        }, ioExecutor);
    }

    public CompletableFuture<Void> recordUniqueKill(UUID killerUuid, UUID victimUuid) {
        if (!settings.enabled() || !databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            var newCount = repository.recordUniqueKill(killerUuid, victimUuid);
            if (newCount.isEmpty()) {
                return;
            }
            long reward = achievements.onUniqueKillMilestone(killerUuid, newCount.getAsLong());
            if (reward > 0) {
                creditAndNotify(killerUuid, reward, "UNIQUE_KILL",
                        "unique kill milestone " + newCount.getAsLong(), null);
            }
        }, ioExecutor).exceptionally(error -> {
            logger.error("Unique kill glyph grant failed for {}", killerUuid, error);
            return null;
        });
    }

    public CompletableFuture<Void> noteBountyClaim(UUID killerUuid) {
        if (!settings.enabled() || !databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            long claims = repository.noteBountyClaim(killerUuid);
            long reward = achievements.onBountyClaimMilestone(killerUuid, claims);
            if (reward > 0) {
                creditAndNotify(killerUuid, reward, "BOUNTY_MILESTONE",
                        "bounty claim milestone " + claims, null);
            }
        }, ioExecutor).exceptionally(error -> {
            logger.error("Bounty claim glyph grant failed for {}", killerUuid, error);
            return null;
        });
    }

    public CompletableFuture<Void> noteAuctionSale(UUID sellerUuid, long salePriceDollars) {
        if (!settings.enabled() || !databaseReady.getAsBoolean() || salePriceDollars <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            long previous = repository.ahSold(sellerUuid);
            long total = repository.addAhSold(sellerUuid, salePriceDollars);
            achievements.onAhSoldMilestone(sellerUuid, total, previous);
        }, ioExecutor).exceptionally(error -> {
            logger.error("Auction sale glyph tracking failed for {}", sellerUuid, error);
            return null;
        });
    }

    public CompletableFuture<List<String>> unlocks(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> repository.unlocks(playerUuid), ioExecutor);
    }

    public CompletableFuture<Boolean> hasUnlock(UUID playerUuid, String productId) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(
                () -> repository.hasUnlock(playerUuid, productId), ioExecutor);
    }

    public CompletableFuture<Optional<Long>> debitPurchase(
            UUID playerUuid, long amount, String productId, UUID actor) {
        if (!settings.enabled() || !databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<Long> newBalance = repository.debit(
                    playerUuid, amount, "SHOP_PURCHASE", "buy " + productId, actor);
            newBalance.ifPresent(balance -> {
                balanceCache.put(playerUuid, balance);
                notifyBalance(playerUuid, balance);
            });
            return newBalance;
        }, ioExecutor);
    }

    public CompletableFuture<Void> recordUnlock(UUID playerUuid, String productId) {
        return CompletableFuture.runAsync(
                () -> repository.addUnlock(playerUuid, productId), ioExecutor);
    }

    public CompletableFuture<EquipResult> equipColor(UUID playerUuid, String productIdOrNone) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(EquipResult.UNAVAILABLE);
        }
        if (productIdOrNone == null || productIdOrNone.equalsIgnoreCase("none")) {
            return CompletableFuture.supplyAsync(() -> {
                repository.clearNameColor(playerUuid);
                nameColorCache.put(playerUuid, Optional.empty());
                notifyColor(playerUuid);
                return EquipResult.CLEARED;
            }, ioExecutor);
        }
        Optional<GlyphProduct> product = GlyphCatalog.find(productIdOrNone);
        if (product.isEmpty() || product.get().type() != GlyphProductType.NAME_COLOR) {
            return CompletableFuture.completedFuture(EquipResult.UNKNOWN);
        }
        GlyphProduct colorProduct = product.get();
        return CompletableFuture.supplyAsync(() -> {
            if (!repository.hasUnlock(playerUuid, colorProduct.id())) {
                return EquipResult.NOT_UNLOCKED;
            }
            repository.setNameColor(playerUuid, colorProduct.payload());
            nameColorCache.put(playerUuid, Optional.of(GlyphDisplay.parseColor(colorProduct.payload())));
            notifyColor(playerUuid);
            return EquipResult.EQUIPPED;
        }, ioExecutor);
    }

    public CompletableFuture<EquipResult> equipTitle(UUID playerUuid, String productIdOrNone) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(EquipResult.UNAVAILABLE);
        }
        if (productIdOrNone == null || productIdOrNone.equalsIgnoreCase("none")) {
            return CompletableFuture.supplyAsync(() -> {
                repository.clearEquippedTitle(playerUuid);
                equippedTitleCache.put(playerUuid, Optional.empty());
                notifyTitle(playerUuid);
                return EquipResult.CLEARED;
            }, ioExecutor);
        }
        if (GlyphTitles.displayText(productIdOrNone).isEmpty()) {
            return CompletableFuture.completedFuture(EquipResult.UNKNOWN);
        }
        return CompletableFuture.supplyAsync(() -> {
            if (!repository.hasUnlock(playerUuid, productIdOrNone)) {
                return EquipResult.NOT_UNLOCKED;
            }
            repository.setEquippedTitle(playerUuid, productIdOrNone);
            equippedTitleCache.put(playerUuid, Optional.of(productIdOrNone));
            notifyTitle(playerUuid);
            return EquipResult.EQUIPPED;
        }, ioExecutor);
    }

    public CompletableFuture<EquipResult> equipDeathStyle(UUID playerUuid, String productIdOrNone) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(EquipResult.UNAVAILABLE);
        }
        if (productIdOrNone == null || productIdOrNone.equalsIgnoreCase("none")) {
            return CompletableFuture.supplyAsync(() -> {
                repository.clearDeathStyle(playerUuid);
                deathStyleCache.put(playerUuid, Optional.empty());
                notifyDeathStyle(playerUuid);
                return EquipResult.CLEARED;
            }, ioExecutor);
        }
        Optional<GlyphProduct> found = GlyphCatalog.find(productIdOrNone);
        if (found.isEmpty() || found.get().type() != GlyphProductType.DEATH_MESSAGE) {
            return CompletableFuture.completedFuture(EquipResult.UNKNOWN);
        }
        GlyphProduct product = found.get();
        return CompletableFuture.supplyAsync(() -> {
            if (!repository.hasUnlock(playerUuid, product.id())) {
                return EquipResult.NOT_UNLOCKED;
            }
            repository.setDeathStyle(playerUuid, product.id());
            deathStyleCache.put(playerUuid, Optional.of(product.id()));
            notifyDeathStyle(playerUuid);
            return EquipResult.EQUIPPED;
        }, ioExecutor);
    }

    public CompletableFuture<EquipResult> setHudEnabled(UUID playerUuid, boolean enabled) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(EquipResult.UNAVAILABLE);
        }
        return CompletableFuture.supplyAsync(() -> {
            repository.setHudEnabled(playerUuid, enabled);
            hudEnabledCache.put(playerUuid, enabled);
            notifyHud(playerUuid);
            return enabled ? EquipResult.EQUIPPED : EquipResult.CLEARED;
        }, ioExecutor);
    }

    public CompletableFuture<AdminResult> adminAdjust(
            UUID playerUuid, AdminOperation operation, long amount, UUID actor) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(AdminResult.UNAVAILABLE);
        }
        return CompletableFuture.supplyAsync(() -> {
            long current = repository.balance(playerUuid);
            long target = switch (operation) {
                case SET -> amount;
                case ADD -> Math.addExact(current, amount);
                case REMOVE -> current - amount;
            };
            if (target < 0) {
                return AdminResult.INSUFFICIENT;
            }
            long delta = target - current;
            if (delta == 0) {
                balanceCache.put(playerUuid, current);
                notifyBalance(playerUuid, current);
                return AdminResult.success(current);
            }
            long newBalance;
            if (delta > 0) {
                newBalance = repository.credit(
                        playerUuid, delta, "ADMIN_ADJUST", "glyphadmin " + operation.name().toLowerCase(),
                        actor);
                long lifetime = repository.lifetimeEarned(playerUuid);
                lifetimeEarnedCache.put(playerUuid, lifetime);
                eventPublisher.publishLifetime(playerUuid, lifetime);
            } else {
                Optional<Long> debited = repository.debit(
                        playerUuid, -delta, "ADMIN_ADJUST", "glyphadmin " + operation.name().toLowerCase(),
                        actor);
                if (debited.isEmpty()) {
                    return AdminResult.INSUFFICIENT;
                }
                newBalance = debited.get();
            }
            balanceCache.put(playerUuid, newBalance);
            notifyBalance(playerUuid, newBalance);
            logger.info("Admin glyph adjustment: {} {} on {} by {} (new balance {})",
                    operation, amount, playerUuid, actor == null ? "console" : actor, newBalance);
            return AdminResult.success(newBalance);
        }, ioExecutor).exceptionally(error -> {
            logger.error("Admin glyph adjustment failed for {}", playerUuid, error);
            return AdminResult.UNAVAILABLE;
        });
    }

    private void creditAndNotify(
            UUID playerUuid, long amount, String type, String reason, UUID actor) {
        long newBalance = repository.credit(playerUuid, amount, type, reason, actor);
        balanceCache.put(playerUuid, newBalance);
        long lifetime = repository.lifetimeEarned(playerUuid);
        lifetimeEarnedCache.put(playerUuid, lifetime);
        eventPublisher.publishLifetime(playerUuid, lifetime);
        notifyBalance(playerUuid, newBalance);
        logger.info("Glyph credit: {} earned {} ({})", playerUuid, amount, reason);
    }

    private void notifyBalance(UUID playerUuid, long balance) {
        for (BiConsumer<UUID, Long> listener : balanceListeners) {
            try {
                listener.accept(playerUuid, balance);
            } catch (Exception e) {
                logger.error("Glyph balance listener failed for {}", playerUuid, e);
            }
        }
    }

    private void notifyColor(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            applyDisplayName(player);
        }
        for (Consumer<UUID> listener : colorListeners) {
            try {
                listener.accept(playerUuid);
            } catch (Exception e) {
                logger.error("Glyph color listener failed for {}", playerUuid, e);
            }
        }
    }

    private void notifyTitle(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            applyDisplayName(player);
        }
        for (Consumer<UUID> listener : titleListeners) {
            try {
                listener.accept(playerUuid);
            } catch (Exception e) {
                logger.error("Glyph title listener failed for {}", playerUuid, e);
            }
        }
    }

    private void notifyDeathStyle(UUID playerUuid) {
        for (Consumer<UUID> listener : deathStyleListeners) {
            try {
                listener.accept(playerUuid);
            } catch (Exception e) {
                logger.error("Glyph death style listener failed for {}", playerUuid, e);
            }
        }
    }

    private void notifyHud(UUID playerUuid) {
        for (Consumer<UUID> listener : hudListeners) {
            try {
                listener.accept(playerUuid);
            } catch (Exception e) {
                logger.error("Glyph hud listener failed for {}", playerUuid, e);
            }
        }
    }

    public enum EquipResult {
        EQUIPPED,
        CLEARED,
        NOT_UNLOCKED,
        UNKNOWN,
        UNAVAILABLE
    }

    public record AdminResult(Status status, long balance) {
        public enum Status { SUCCESS, INSUFFICIENT, UNAVAILABLE }

        static AdminResult success(long balance) {
            return new AdminResult(Status.SUCCESS, balance);
        }

        static final AdminResult INSUFFICIENT =
                new AdminResult(Status.INSUFFICIENT, 0);
        static final AdminResult UNAVAILABLE =
                new AdminResult(Status.UNAVAILABLE, 0);
    }
}
