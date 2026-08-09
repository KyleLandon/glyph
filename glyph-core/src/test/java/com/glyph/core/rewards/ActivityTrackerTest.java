package com.glyph.core.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityTrackerTest {

    @Test
    void recordsAccumulateAndDrainResets() {
        ActivityTracker tracker = new ActivityTracker();
        UUID player = UUID.randomUUID();

        tracker.record(player, 150);
        tracker.record(player, ActivityTracker.BLOCK_UNITS);

        assertThat(tracker.drain(player)).isEqualTo(250);
        assertThat(tracker.drain(player)).isZero();
    }

    @Test
    void clearDropsPendingActivity() {
        ActivityTracker tracker = new ActivityTracker();
        UUID player = UUID.randomUUID();
        tracker.record(player, 500);

        tracker.clear(player);

        assertThat(tracker.drain(player)).isZero();
    }

    @Test
    void nonPositiveAmountsIgnored() {
        ActivityTracker tracker = new ActivityTracker();
        UUID player = UUID.randomUUID();

        tracker.record(player, 0);
        tracker.record(player, -100);

        assertThat(tracker.drain(player)).isZero();
    }
}
