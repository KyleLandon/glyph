-- Forever World player warps and chest shops. Anarchy never writes these
-- tables (ADR-013 / server.role smp).

CREATE TABLE player_warps (
    name VARCHAR(16) NOT NULL,
    owner_uuid UUID NOT NULL,
    market VARCHAR(16) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (market, name),
    CONSTRAINT chk_warp_market CHECK (market IN ('anarchy', 'smp'))
);

CREATE INDEX idx_player_warps_owner
    ON player_warps (owner_uuid, market);

CREATE TABLE chest_shops (
    id UUID PRIMARY KEY,
    owner_uuid UUID NOT NULL,
    market VARCHAR(16) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    mode VARCHAR(8) NOT NULL,
    price BIGINT NOT NULL,
    item_data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_shop_market CHECK (market IN ('anarchy', 'smp')),
    CONSTRAINT chk_shop_mode CHECK (mode IN ('SELL', 'BUY')),
    CONSTRAINT chk_shop_price CHECK (price > 0),
    CONSTRAINT uq_chest_shop_block UNIQUE (market, world, x, y, z)
);

CREATE INDEX idx_chest_shops_owner
    ON chest_shops (owner_uuid, market);
