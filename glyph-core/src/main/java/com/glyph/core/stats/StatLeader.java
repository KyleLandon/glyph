package com.glyph.core.stats;

import java.util.UUID;

/** One row on a kills/deaths leaderboard. */
public record StatLeader(UUID uuid, String username, long value) {
}
