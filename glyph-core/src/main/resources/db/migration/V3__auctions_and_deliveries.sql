-- Auction house and delivery queue (GDD sections 21-23, 52-53).
-- Items are immutable serialized snapshots (BYTEA); the summary JSONB copy
-- exists so browsing/search never deserializes item bytes in SQL.

CREATE TABLE auction_listings (
    id             UUID PRIMARY KEY,
    seller_uuid    UUID        NOT NULL,
    seller_account UUID        NOT NULL REFERENCES accounts (id),
    item_data      BYTEA       NOT NULL,
    item_summary   JSONB       NOT NULL,
    price          BIGINT      NOT NULL,
    listing_fee    BIGINT      NOT NULL DEFAULT 0,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    buyer_uuid     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    sold_at        TIMESTAMPTZ,

    CONSTRAINT chk_auction_price_positive CHECK (price > 0),
    CONSTRAINT chk_auction_listing_fee_non_negative CHECK (listing_fee >= 0),
    CONSTRAINT chk_auction_status CHECK (status IN ('ACTIVE', 'SOLD', 'CANCELLED', 'EXPIRED'))
);

-- Browse/search always filters on status; expiry sweep scans (status, expires_at).
CREATE INDEX idx_auction_listings_status_expires ON auction_listings (status, expires_at);
CREATE INDEX idx_auction_listings_status_created ON auction_listings (status, created_at DESC);
CREATE INDEX idx_auction_listings_status_price ON auction_listings (status, price);
CREATE INDEX idx_auction_listings_seller ON auction_listings (seller_uuid, status);

CREATE TABLE deliveries (
    id             UUID PRIMARY KEY,
    recipient_uuid UUID        NOT NULL,
    type           VARCHAR(32) NOT NULL,
    payload        BYTEA       NOT NULL,
    metadata       JSONB,
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_at     TIMESTAMPTZ,

    CONSTRAINT chk_delivery_status CHECK (status IN ('PENDING', 'CLAIMED'))
);

CREATE INDEX idx_deliveries_recipient_status ON deliveries (recipient_uuid, status);
