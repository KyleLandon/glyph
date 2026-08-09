package com.glyph.api.economy;

/**
 * Who owns an economy account (GDD section 15). Stored in
 * {@code accounts.owner_type}.
 */
public enum AccountType {
    PLAYER,
    COMPANY,
    SYSTEM,
    ESCROW
}
