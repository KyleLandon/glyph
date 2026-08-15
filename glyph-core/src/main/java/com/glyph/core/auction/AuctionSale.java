package com.glyph.core.auction;

import java.util.UUID;

/** Fired after a committed auction sale. */
public record AuctionSale(UUID buyerUuid, UUID sellerUuid, long priceDollars) {
}
