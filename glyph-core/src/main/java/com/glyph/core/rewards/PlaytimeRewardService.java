package com.glyph.core.rewards;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TransactionType;
import com.glyph.core.config.PlaytimeRewardSettings;
import com.glyph.core.economy.EconomyRepository;
import com.glyph.core.economy.EconomyService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * The launch money faucet (GDD section 16): a fixed payment per window of
 * demonstrated activity, minted as {@code SYSTEM_REWARD} through the normal
 * ledger so supply growth is fully measurable (GDD 125).
 *
 * <p>AFK resistance: payment requires {@link ActivityTracker} units earned
 * during the window (blocks broken/placed or distance moved). Idle players
 * — including AFK stands — accumulate nothing and are skipped.</p>
 */
public final class PlaytimeRewardService {

    private final ActivityTracker tracker;
    private final EconomyRepository economyRepository;
    private final EconomyService economyService;
    private final PlaytimeRewardSettings settings;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    public PlaytimeRewardService(
            ActivityTracker tracker,
            EconomyRepository economyRepository,
            EconomyService economyService,
            PlaytimeRewardSettings settings,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.economyRepository = Objects.requireNonNull(economyRepository, "economyRepository");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public PlaytimeRewardSettings settings() {
        return settings;
    }

    /**
     * Ends the current window: pays every listed player who met the activity
     * bar and resets their counters. Returns the UUIDs that were paid so the
     * caller can notify them in-game.
     *
     * <p>When the database is down the window is skipped and counters are
     * left in place — activity carries into the next window instead of being
     * lost.</p>
     */
    public CompletableFuture<List<UUID>> payoutWindow(List<UUID> onlinePlayers) {
        if (!settings.enabled() || onlinePlayers.isEmpty() || !databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> paid = new ArrayList<>();
            for (UUID player : onlinePlayers) {
                long activity = tracker.drain(player);
                if (activity < settings.minActivityUnits()) {
                    continue;
                }
                try {
                    EconomyRepository.MutationOutcome outcome = economyRepository.externalAdjust(
                            player, AdminOperation.ADD, settings.amountMinor(),
                            TransactionType.SYSTEM_REWARD, "active playtime reward");
                    if (outcome.result().isSuccess()) {
                        economyService.publishBalanceChange(
                                player, Money.ofMinor(outcome.sourceBalanceAfter()));
                        paid.add(player);
                    }
                } catch (Exception e) {
                    logger.error("Playtime reward failed for {}", player, e);
                }
            }
            if (!paid.isEmpty()) {
                logger.info("Playtime rewards: paid {} to {} active player(s)",
                        settings.amountMinor(), paid.size());
            }
            return paid;
        }, ioExecutor);
    }
}
