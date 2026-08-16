-- Forever World homes. Anarchy never writes this table (ADR-013).

CREATE TABLE player_homes (
    player_uuid UUID NOT NULL,
    market VARCHAR(16) NOT NULL,
    name VARCHAR(16) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (player_uuid, market, name),
    CONSTRAINT chk_home_market CHECK (market IN ('anarchy', 'smp'))
);

CREATE INDEX idx_player_homes_player_market
    ON player_homes (player_uuid, market);
