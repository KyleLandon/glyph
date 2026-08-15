CREATE TABLE IF NOT EXISTS glyph_unique_kills (
  killer_uuid UUID NOT NULL REFERENCES players (uuid),
  victim_uuid UUID NOT NULL REFERENCES players (uuid),
  first_killed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (killer_uuid, victim_uuid)
);
CREATE INDEX idx_glyph_unique_kills_killer ON glyph_unique_kills (killer_uuid);

ALTER TABLE players
  ADD COLUMN IF NOT EXISTS glyph_bounties_claimed BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS glyph_equipped_title VARCHAR(64),
  ADD COLUMN IF NOT EXISTS glyph_death_style VARCHAR(64),
  ADD COLUMN IF NOT EXISTS glyph_hud_enabled BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS glyph_ah_sold BIGINT NOT NULL DEFAULT 0;

-- Clean obsolete purchasable unlocks that violate the new rules (trim + discord shop ids)
DELETE FROM glyph_unlocks WHERE product_id LIKE 'trim_%' OR product_id LIKE 'discord_%';
