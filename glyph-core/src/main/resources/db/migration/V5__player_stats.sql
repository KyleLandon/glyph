-- Aggregated player statistics (GDD sections 30, 56).
-- One row per player, written only by buffered batch upserts (GDD 104) —
-- never one UPDATE per block action.

CREATE TABLE player_stats (
    player_uuid       UUID PRIMARY KEY REFERENCES players (uuid),
    kills             BIGINT      NOT NULL DEFAULT 0,
    deaths            BIGINT      NOT NULL DEFAULT 0,
    mob_kills         BIGINT      NOT NULL DEFAULT 0,
    blocks_broken     BIGINT      NOT NULL DEFAULT 0,
    blocks_placed     BIGINT      NOT NULL DEFAULT 0,
    distance_cm       BIGINT      NOT NULL DEFAULT 0,
    auction_sales     BIGINT      NOT NULL DEFAULT 0,
    auction_purchases BIGINT      NOT NULL DEFAULT 0,
    bounties_claimed  BIGINT      NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Leaderboards (GDD 31) sort by individual counters.
CREATE INDEX idx_player_stats_kills ON player_stats (kills DESC);
CREATE INDEX idx_player_stats_deaths ON player_stats (deaths DESC);
