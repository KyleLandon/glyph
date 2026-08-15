-- Discord identity linking + alpha access entitlement (ADR-012).

CREATE TABLE discord_links (
    minecraft_uuid  UUID PRIMARY KEY REFERENCES players (uuid) ON DELETE CASCADE,
    discord_user_id BIGINT NOT NULL,
    linked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_discord_links_user UNIQUE (discord_user_id)
);

CREATE TABLE discord_link_codes (
    code            VARCHAR(16) PRIMARY KEY,
    minecraft_uuid  UUID NOT NULL REFERENCES players (uuid) ON DELETE CASCADE,
    expires_at      TIMESTAMPTZ NOT NULL,
    consumed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_discord_link_codes_player
    ON discord_link_codes (minecraft_uuid)
    WHERE consumed_at IS NULL;

CREATE TABLE player_access (
    minecraft_uuid  UUID PRIMARY KEY REFERENCES players (uuid) ON DELETE CASCADE,
    alpha           BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_player_access_alpha
    ON player_access (minecraft_uuid)
    WHERE alpha = TRUE;
