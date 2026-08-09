package com.glyph.core.rewards;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free per-window activity counters for playtime rewards (GDD 16).
 *
 * <p>Units are centimeter-equivalents: one meter moved = 100, one block
 * broken or placed = 100. Fed by {@code StatsListener} on event threads;
 * drained by the payout loop each window.</p>
 */
public final class ActivityTracker {

    /** Activity units credited per block broken or placed. */
    public static final long BLOCK_UNITS = 100;

    private final ConcurrentMap<UUID, LongAdder> units = new ConcurrentHashMap<>();

    public void record(UUID playerUuid, long amount) {
        if (amount <= 0) {
            return;
        }
        units.computeIfAbsent(playerUuid, ignored -> new LongAdder()).add(amount);
    }

    /** Returns and resets the player's accumulated activity. */
    public long drain(UUID playerUuid) {
        LongAdder adder = units.remove(playerUuid);
        return adder == null ? 0 : adder.sum();
    }

    /** Drops counters when a player disconnects mid-window. */
    public void clear(UUID playerUuid) {
        units.remove(playerUuid);
    }
}
