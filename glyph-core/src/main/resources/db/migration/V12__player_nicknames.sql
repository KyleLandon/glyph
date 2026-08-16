-- Forever World character names. Anarchy never writes this table (ADR-013).

CREATE TABLE player_nicknames (
    player_uuid UUID NOT NULL,
    market VARCHAR(16) NOT NULL,
    nickname VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (player_uuid, market),
    CONSTRAINT chk_nick_market CHECK (market IN ('anarchy', 'smp'))
);

CREATE UNIQUE INDEX uq_player_nicknames_market_lower
    ON player_nicknames (market, lower(nickname));
