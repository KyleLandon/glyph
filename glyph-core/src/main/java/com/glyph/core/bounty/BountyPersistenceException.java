package com.glyph.core.bounty;

/** Wraps SQL failures from the bounty repository. */
public final class BountyPersistenceException extends RuntimeException {

    public BountyPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
