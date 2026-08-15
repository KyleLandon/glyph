ALTER TABLE accounts
  ADD COLUMN glyphs_balance BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN glyphs_lifetime_earned BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN glyphs_lifetime_spent BIGINT NOT NULL DEFAULT 0,
  ADD CONSTRAINT chk_accounts_glyphs_non_negative CHECK (glyphs_balance >= 0);

ALTER TABLE players
  ADD COLUMN glyph_name_color VARCHAR(32),
  ADD COLUMN glyph_player_kills BIGINT NOT NULL DEFAULT 0;

CREATE TABLE glyph_ledger (
  id UUID PRIMARY KEY,
  player_uuid UUID NOT NULL REFERENCES players(uuid),
  amount BIGINT NOT NULL,
  direction VARCHAR(8) NOT NULL,
  type VARCHAR(32) NOT NULL,
  reason TEXT,
  actor_uuid UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_glyph_ledger_amount_positive CHECK (amount > 0),
  CONSTRAINT chk_glyph_ledger_direction CHECK (direction IN ('CREDIT','DEBIT'))
);
CREATE INDEX idx_glyph_ledger_player ON glyph_ledger (player_uuid, created_at DESC);

CREATE TABLE glyph_unlocks (
  player_uuid UUID NOT NULL REFERENCES players(uuid),
  product_id VARCHAR(64) NOT NULL,
  purchased_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (player_uuid, product_id)
);
