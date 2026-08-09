-- Glyph initial schema (GDD sections 49-51).
-- Players, economy accounts and the transaction ledger.
-- Balances are BIGINT minor units (cents). Never floating point.

CREATE TABLE players (
    uuid             UUID PRIMARY KEY,
    username         VARCHAR(16) NOT NULL,
    first_join       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_join        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen        TIMESTAMPTZ NOT NULL DEFAULT now(),
    playtime_seconds BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_players_username ON players (username);
CREATE INDEX idx_players_last_seen ON players (last_seen);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    owner_type      VARCHAR(16) NOT NULL,
    owner_uuid      UUID,
    balance         BIGINT      NOT NULL DEFAULT 0,
    lifetime_earned BIGINT      NOT NULL DEFAULT 0,
    lifetime_spent  BIGINT      NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE UNIQUE INDEX uq_accounts_owner ON accounts (owner_type, owner_uuid);

CREATE TABLE transactions (
    id                  UUID PRIMARY KEY,
    source_account      UUID REFERENCES accounts (id),
    destination_account UUID REFERENCES accounts (id),
    amount              BIGINT      NOT NULL,
    type                VARCHAR(32) NOT NULL,
    reason              TEXT,
    related_entity      UUID,
    metadata            JSONB,
    actor_uuid          UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_transactions_source ON transactions (source_account);
CREATE INDEX idx_transactions_destination ON transactions (destination_account);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
CREATE INDEX idx_transactions_type ON transactions (type);
CREATE INDEX idx_transactions_related ON transactions (related_entity);
