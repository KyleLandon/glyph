package com.glyph.core.bounty;

import com.glyph.core.bounty.BountyRepository.KillOutcome;
import com.glyph.core.bounty.BountyRepository.PlaceResult;
import com.glyph.core.bounty.BountyRepository.PlaceStatus;
import com.glyph.core.bounty.BountyRepository.TargetTotal;
import com.glyph.core.config.BountySettings;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Bounty orchestration (GDD section 25). Validation here, atomicity in the
 * repository, everything on the async executor.
 */
public final class BountyService {

    private final BountyRepository repository;
    private final BountySettings settings;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    public BountyService(
            BountyRepository repository,
            BountySettings settings,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public BountySettings settings() {
        return settings;
    }

    public CompletableFuture<PlaceResult> place(UUID target, UUID creator, long amountMinor) {
        if (amountMinor < settings.minimumMinor() || target.equals(creator)) {
            return CompletableFuture.completedFuture(PlaceResult.failure(PlaceStatus.FAILED));
        }
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(PlaceResult.failure(PlaceStatus.FAILED));
        }
        return CompletableFuture
                .supplyAsync(() -> repository.place(target, creator, amountMinor), ioExecutor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Bounty placement failed: {} on {}", creator, target, error);
                    } else if (result.status() == PlaceStatus.SUCCESS) {
                        logger.info("Bounty placed: {} on {} for {}", creator, target, amountMinor);
                    }
                })
                .exceptionally(error -> PlaceResult.failure(PlaceStatus.FAILED));
    }

    /**
     * Records the kill and pays active bounties. Suspicious redemptions
     * (withheld payouts) are logged for moderation (GDD 25) — never
     * auto-punished.
     */
    public CompletableFuture<KillOutcome> recordKill(
            UUID killer, UUID victim, String world, int x, int y, int z,
            String weaponJson, String cause) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(new KillOutcome(0, 0, false));
        }
        return CompletableFuture
                .supplyAsync(() -> repository.recordKill(
                        killer, victim, world, x, y, z, weaponJson, cause,
                        settings.sameVictimCooldownMinutes()), ioExecutor)
                .whenComplete((outcome, error) -> {
                    if (error != null) {
                        logger.error("Kill record failed: {} -> {}", killer, victim, error);
                    } else if (outcome.withheld()) {
                        logger.warn("SUSPICIOUS bounty redemption withheld: {} killed {} again "
                                        + "within {} minute(s) — bounties stay active",
                                killer, victim, settings.sameVictimCooldownMinutes());
                    } else if (outcome.bountyPaidMinor() > 0) {
                        logger.info("Bounty paid: {} claimed {} ({} bounty(ies)) for killing {}",
                                killer, outcome.bountyPaidMinor(),
                                outcome.bountiesClaimed(), victim);
                    }
                })
                .exceptionally(error -> new KillOutcome(0, 0, false));
    }

    public CompletableFuture<Long> activeTotal(UUID target) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(0L);
        }
        return CompletableFuture.supplyAsync(() -> repository.activeTotal(target), ioExecutor);
    }

    public CompletableFuture<List<TargetTotal>> topTargets(int limit) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(List.of());
        }
        int capped = Math.clamp(limit, 1, 25);
        return CompletableFuture.supplyAsync(() -> repository.topTargets(capped), ioExecutor);
    }
}
