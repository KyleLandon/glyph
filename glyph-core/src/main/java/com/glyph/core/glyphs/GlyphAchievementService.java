package com.glyph.core.glyphs;

import com.glyph.core.config.GlyphCurrencySettings;
import com.glyph.core.event.GlyphEventPublisher;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

/** Achievement-based Glyph grants — not sold in the shop (see {@code docs/GLYPHS.md}). */
public final class GlyphAchievementService {

    static final String MILESTONE_FIRST_BOUNTY = "milestone_first_bounty";
    static final String ACH_BLOODED_I = "ach_blooded_i";
    static final String ACH_BLOODED_II = "ach_blooded_ii";
    static final String TITLE_BLOODED = "title_blooded";
    static final String ACH_BLOODED_IV = "ach_blooded_iv";
    static final String ACH_HUNTER_II = "ach_hunter_ii";
    static final String TITLE_HUNTER = "title_hunter";
    static final String TITLE_BROKER = "title_broker";

    private static final long AH_BROKER_THRESHOLD = 1_000_000L;

    private final GlyphsRepository repository;
    private final GlyphCurrencySettings settings;
    private final SchedulerAdapter scheduler;
    private final GlyphEventPublisher eventPublisher;
    private final Logger logger;

    public GlyphAchievementService(
            GlyphsRepository repository,
            GlyphCurrencySettings settings,
            SchedulerAdapter scheduler,
            GlyphEventPublisher eventPublisher,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * @return credited amount (0 when no new milestone)
     */
    long onUniqueKillMilestone(UUID killerUuid, long uniqueCount) {
        return switch ((int) uniqueCount) {
            case 10 -> grant(killerUuid, 2L, ACH_BLOODED_I, "Unique kill milestone: 10 victims");
            case 25 -> grant(killerUuid, 3L, ACH_BLOODED_II, "Unique kill milestone: 25 victims");
            case 50 -> grant(killerUuid, 5L, TITLE_BLOODED, "Unique kill milestone: 50 victims");
            case 100 -> grant(killerUuid, 10L, ACH_BLOODED_IV, "Unique kill milestone: 100 victims");
            default -> 0L;
        };
    }

    /**
     * @return credited amount (0 when no new milestone)
     */
    long onBountyClaimMilestone(UUID killerUuid, long claimCount) {
        if (claimCount == 1) {
            long reward = settings.firstBountyReward();
            if (reward > 0 && repository.tryClaimMilestone(killerUuid, MILESTONE_FIRST_BOUNTY)) {
                notify(killerUuid, "First bounty claimed — earned " + settings.symbol() + reward + ".");
                return reward;
            }
            return 0L;
        }
        if (claimCount == 10) {
            return grant(killerUuid, 5L, ACH_HUNTER_II, "Bounty hunter milestone: 10 claims");
        }
        if (claimCount == 25) {
            return grantUnlock(killerUuid, TITLE_HUNTER, "Bounty hunter title unlocked: Bounty Hunter");
        }
        return 0L;
    }

    void onAhSoldMilestone(UUID sellerUuid, long totalSold, long previousTotal) {
        if (previousTotal < AH_BROKER_THRESHOLD && totalSold >= AH_BROKER_THRESHOLD) {
            if (repository.tryClaimMilestone(sellerUuid, TITLE_BROKER)) {
                repository.addUnlock(sellerUuid, TITLE_BROKER);
                eventPublisher.publishTitle(sellerUuid);
                notify(sellerUuid, "Auction broker title unlocked: Broker.");
                logger.info("AH broker title unlocked for {} at {} sold", sellerUuid, totalSold);
            }
        }
    }

    private long grant(UUID playerUuid, long amount, String milestoneId, String message) {
        if (!repository.tryClaimMilestone(playerUuid, milestoneId)) {
            return 0L;
        }
        if (GlyphTitles.isTitleUnlock(milestoneId)) {
            repository.addUnlock(playerUuid, milestoneId);
            eventPublisher.publishTitle(playerUuid);
        }
        notify(playerUuid, message + " — earned " + settings.symbol() + amount + ".");
        return amount;
    }

    private long grantUnlock(UUID playerUuid, String unlockId, String message) {
        if (!repository.tryClaimMilestone(playerUuid, unlockId)) {
            return 0L;
        }
        repository.addUnlock(playerUuid, unlockId);
        if (GlyphTitles.isTitleUnlock(unlockId)) {
            eventPublisher.publishTitle(playerUuid);
        }
        notify(playerUuid, message + ".");
        return 0L;
    }

    private void notify(UUID playerUuid, String message) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            return;
        }
        scheduler.runForEntity(player, () -> player.sendMessage(
                Component.text(message, NamedTextColor.LIGHT_PURPLE)), null);
    }
}
