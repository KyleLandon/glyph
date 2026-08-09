package com.glyph.core.auction;

/** Wraps SQL failures from the auction repository. */
public final class AuctionPersistenceException extends RuntimeException {

    public AuctionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
