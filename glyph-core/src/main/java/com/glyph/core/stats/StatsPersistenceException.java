package com.glyph.core.stats;

/** Wraps SQL failures from the stats repository. */
public final class StatsPersistenceException extends RuntimeException {

    public StatsPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
