package com.glyph.core.stats;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free in-memory statistic counters (GDD section 104): gameplay events
 * increment {@link LongAdder}s on whatever thread they fire on; the flush
 * loop drains snapshots for batch persistence.
 */
public final class StatsBuffer {

    private final ConcurrentMap<UUID, ConcurrentMap<StatType, LongAdder>> counters =
            new ConcurrentHashMap<>();

    public void increment(UUID playerUuid, StatType type, long amount) {
        if (amount <= 0) {
            return;
        }
        counters.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, ignored -> new LongAdder())
                .add(amount);
    }

    /** Removes and returns all pending deltas. */
    public Map<UUID, Map<StatType, Long>> drain() {
        Map<UUID, Map<StatType, Long>> snapshot = new HashMap<>();
        for (UUID playerUuid : counters.keySet()) {
            Map<StatType, Long> deltas = drainPlayer(playerUuid);
            if (!deltas.isEmpty()) {
                snapshot.put(playerUuid, deltas);
            }
        }
        return snapshot;
    }

    /** Removes and returns one player's pending deltas. */
    public Map<StatType, Long> drainPlayer(UUID playerUuid) {
        ConcurrentMap<StatType, LongAdder> player = counters.remove(playerUuid);
        if (player == null) {
            return Map.of();
        }
        Map<StatType, Long> deltas = new EnumMap<>(StatType.class);
        player.forEach((type, adder) -> {
            long value = adder.sumThenReset();
            if (value > 0) {
                deltas.put(type, value);
            }
        });
        return deltas;
    }

    /** Puts deltas back after a failed flush so nothing is lost. */
    public void restore(Map<UUID, Map<StatType, Long>> deltas) {
        deltas.forEach((playerUuid, playerDeltas) ->
                playerDeltas.forEach((type, value) -> increment(playerUuid, type, value)));
    }
}
