-- Bounties and the kill log (GDD sections 25, 33, 54-55).
--
-- Escrow model: bounty money moves creator -> escrow account at placement
-- (BOUNTY_ESCROW) and escrow -> killer at payout (BOUNTY_REWARD). Money is
-- conserved and always visible in the ledger; a bounty is never created
-- unless its escrow transfer commits in the same transaction.

INSERT INTO accounts (id, owner_type, owner_uuid)
VALUES ('00000000-0000-0000-0000-000000000001', 'ESCROW',
        '00000000-0000-0000-0000-000000000001');

CREATE TABLE bounties (
    id           UUID PRIMARY KEY,
    target_uuid  UUID        NOT NULL,
    creator_uuid UUID        NOT NULL,
    amount       BIGINT      NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    claimed_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at   TIMESTAMPTZ,

    CONSTRAINT chk_bounties_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_bounties_status CHECK (status IN ('ACTIVE', 'CLAIMED'))
);

CREATE INDEX idx_bounties_target_status ON bounties (target_uuid, status);
CREATE INDEX idx_bounties_creator ON bounties (creator_uuid);

CREATE TABLE player_kills (
    id            UUID PRIMARY KEY,
    killer_uuid   UUID        NOT NULL,
    victim_uuid   UUID        NOT NULL,
    world         VARCHAR(64) NOT NULL,
    x             INTEGER     NOT NULL,
    y             INTEGER     NOT NULL,
    z             INTEGER     NOT NULL,
    weapon        JSONB,
    cause         VARCHAR(32) NOT NULL,
    bounty_amount BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_player_kills_killer ON player_kills (killer_uuid, created_at DESC);
CREATE INDEX idx_player_kills_victim ON player_kills (victim_uuid, created_at DESC);
-- Anti-abuse: repeated same-victim kill detection (GDD section 25).
CREATE INDEX idx_player_kills_pair ON player_kills (killer_uuid, victim_uuid, created_at DESC);
