-- Separate auction houses per backend. Same $ wallet; items never cross.
-- Existing rows are anarchy listings/deliveries from before SMP existed.

ALTER TABLE auction_listings
    ADD COLUMN market VARCHAR(16) NOT NULL DEFAULT 'anarchy';

ALTER TABLE auction_listings
    ADD CONSTRAINT chk_auction_market CHECK (market IN ('anarchy', 'smp'));

CREATE INDEX idx_auction_listings_market_status_expires
    ON auction_listings (market, status, expires_at);
CREATE INDEX idx_auction_listings_market_status_created
    ON auction_listings (market, status, created_at DESC);
CREATE INDEX idx_auction_listings_market_seller
    ON auction_listings (market, seller_uuid, status);

ALTER TABLE deliveries
    ADD COLUMN market VARCHAR(16) NOT NULL DEFAULT 'anarchy';

ALTER TABLE deliveries
    ADD CONSTRAINT chk_delivery_market CHECK (market IN ('anarchy', 'smp'));

CREATE INDEX idx_deliveries_market_recipient_status
    ON deliveries (market, recipient_uuid, status);
