package com.glyph.core.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TopBalance;
import com.glyph.api.economy.TransactionType;
import com.glyph.api.economy.TransferResult;
import com.glyph.core.config.PlaytimeRewardSettings;
import com.glyph.core.economy.EconomyRepository;
import com.glyph.core.economy.EconomyService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PlaytimeRewardServiceTest {

    /** Records mints; every adjust succeeds with a fixed new balance. */
    private static final class RecordingRepository implements EconomyRepository {
        final List<UUID> minted = new ArrayList<>();
        final List<TransactionType> types = new ArrayList<>();

        @Override
        public MutationOutcome externalAdjust(UUID playerUuid, AdminOperation operation,
                                              long amountMinor, TransactionType type,
                                              String reason) {
            minted.add(playerUuid);
            types.add(type);
            return new MutationOutcome(
                    TransferResult.success(UUID.randomUUID(), Money.ofMinor(amountMinor)),
                    amountMinor, -1);
        }

        @Override
        public Optional<Long> balanceMinor(UUID playerUuid) {
            return Optional.of(0L);
        }

        @Override
        public MutationOutcome transfer(UUID source, UUID destination,
                                        long amountMinor, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MutationOutcome adminAdjust(UUID playerUuid, AdminOperation operation,
                                           long amountMinor, UUID actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean ensureAccount(UUID playerUuid) {
            return true;
        }

        @Override
        public List<TopBalance> topBalances(int limit) {
            return List.of();
        }

        @Override
        public List<LedgerEntry> history(UUID playerUuid, int limit) {
            return List.of();
        }
    }

    private static final PlaytimeRewardSettings SETTINGS =
            new PlaytimeRewardSettings(true, 15, 1_000, 20);

    private static PlaytimeRewardService service(
            ActivityTracker tracker, RecordingRepository repository, boolean dbReady) {
        EconomyService economyService = new EconomyService(
                repository, () -> dbReady, Runnable::run, LoggerFactory.getLogger("test"));
        return new PlaytimeRewardService(tracker, repository, economyService, SETTINGS,
                () -> dbReady, Runnable::run, LoggerFactory.getLogger("test"));
    }

    @Test
    void activePlayerPaidIdlePlayerSkipped() {
        ActivityTracker tracker = new ActivityTracker();
        RecordingRepository repository = new RecordingRepository();
        UUID active = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        tracker.record(active, SETTINGS.minActivityUnits());

        List<UUID> paid = service(tracker, repository, true)
                .payoutWindow(List.of(active, idle)).join();

        assertThat(paid).containsExactly(active);
        assertThat(repository.minted).containsExactly(active);
        assertThat(repository.types).containsExactly(TransactionType.SYSTEM_REWARD);
    }

    @Test
    void activityJustBelowThresholdDoesNotPay() {
        ActivityTracker tracker = new ActivityTracker();
        RecordingRepository repository = new RecordingRepository();
        UUID player = UUID.randomUUID();
        tracker.record(player, SETTINGS.minActivityUnits() - 1);

        List<UUID> paid = service(tracker, repository, true)
                .payoutWindow(List.of(player)).join();

        assertThat(paid).isEmpty();
        assertThat(repository.minted).isEmpty();
    }

    @Test
    void activityResetsBetweenWindows() {
        ActivityTracker tracker = new ActivityTracker();
        RecordingRepository repository = new RecordingRepository();
        UUID player = UUID.randomUUID();
        tracker.record(player, SETTINGS.minActivityUnits());
        PlaytimeRewardService service = service(tracker, repository, true);

        service.payoutWindow(List.of(player)).join();
        // Second window with no new activity: nothing to pay.
        List<UUID> second = service.payoutWindow(List.of(player)).join();

        assertThat(second).isEmpty();
        assertThat(repository.minted).hasSize(1);
    }

    @Test
    void databaseDownSkipsWindowAndKeepsActivity() {
        ActivityTracker tracker = new ActivityTracker();
        RecordingRepository repository = new RecordingRepository();
        UUID player = UUID.randomUUID();
        tracker.record(player, SETTINGS.minActivityUnits());

        List<UUID> paid = service(tracker, repository, false)
                .payoutWindow(List.of(player)).join();

        assertThat(paid).isEmpty();
        assertThat(repository.minted).isEmpty();
        // Activity survives for the next window.
        assertThat(tracker.drain(player)).isEqualTo(SETTINGS.minActivityUnits());
    }

    @Test
    void disabledSettingPaysNothing() {
        ActivityTracker tracker = new ActivityTracker();
        RecordingRepository repository = new RecordingRepository();
        UUID player = UUID.randomUUID();
        tracker.record(player, SETTINGS.minActivityUnits());
        PlaytimeRewardSettings disabled = new PlaytimeRewardSettings(false, 15, 1_000, 20);
        EconomyService economyService = new EconomyService(
                repository, () -> true, Runnable::run, LoggerFactory.getLogger("test"));
        PlaytimeRewardService service = new PlaytimeRewardService(
                tracker, repository, economyService, disabled,
                () -> true, Runnable::run, LoggerFactory.getLogger("test"));

        assertThat(service.payoutWindow(List.of(player)).join()).isEmpty();
        assertThat(repository.minted).isEmpty();
    }
}
