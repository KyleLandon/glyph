package com.glyph.core.stats;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract for aggregated statistics (GDD sections 56, 104). */
public interface StatsRepository {

    /** Applies buffered deltas as one batched upsert. */
    void addDeltas(Map<UUID, Map<StatType, Long>> deltas);

    Optional<PlayerStats> find(UUID playerUuid);
}
