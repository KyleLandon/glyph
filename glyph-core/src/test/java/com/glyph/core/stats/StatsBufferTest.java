package com.glyph.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class StatsBufferTest {

    @Test
    void incrementsAggregateAndDrainResets() {
        StatsBuffer buffer = new StatsBuffer();
        UUID player = UUID.randomUUID();

        buffer.increment(player, StatType.BLOCKS_BROKEN, 1);
        buffer.increment(player, StatType.BLOCKS_BROKEN, 2);
        buffer.increment(player, StatType.KILLS, 1);

        Map<StatType, Long> deltas = buffer.drainPlayer(player);
        assertThat(deltas).containsEntry(StatType.BLOCKS_BROKEN, 3L)
                .containsEntry(StatType.KILLS, 1L);
        assertThat(buffer.drainPlayer(player)).isEmpty();
    }

    @Test
    void nonPositiveIncrementsIgnored() {
        StatsBuffer buffer = new StatsBuffer();
        UUID player = UUID.randomUUID();

        buffer.increment(player, StatType.KILLS, 0);
        buffer.increment(player, StatType.KILLS, -5);

        assertThat(buffer.drainPlayer(player)).isEmpty();
    }

    @Test
    void restorePutsDeltasBack() {
        StatsBuffer buffer = new StatsBuffer();
        UUID player = UUID.randomUUID();
        buffer.increment(player, StatType.DEATHS, 2);

        Map<UUID, Map<StatType, Long>> drained = buffer.drain();
        assertThat(drained).containsKey(player);
        buffer.restore(drained);

        assertThat(buffer.drainPlayer(player)).containsEntry(StatType.DEATHS, 2L);
    }

    @Test
    void concurrentIncrementsAreLossless() throws Exception {
        StatsBuffer buffer = new StatsBuffer();
        UUID player = UUID.randomUUID();
        int threads = 8;
        int perThread = 10_000;

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            CountDownLatch start = new CountDownLatch(1);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        buffer.increment(player, StatType.DISTANCE_CM, 1);
                    }
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(buffer.drainPlayer(player))
                .containsEntry(StatType.DISTANCE_CM, (long) threads * perThread);
    }
}
