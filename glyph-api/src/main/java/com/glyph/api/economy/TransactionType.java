package com.glyph.api.economy;

/**
 * Ledger entry categories (GDD section 19). Stored in
 * {@code transactions.type}; analytics group money supply by these.
 */
public enum TransactionType {
    PLAYER_TRANSFER,
    AUCTION_PURCHASE,
    AUCTION_FEE,
    BOUNTY_ESCROW,
    BOUNTY_REWARD,
    ADMIN_ADJUSTMENT,
    CONTRACT_PAYMENT,
    BUSINESS_PAYMENT,
    SYSTEM_REWARD,
    SYSTEM_SINK
}
