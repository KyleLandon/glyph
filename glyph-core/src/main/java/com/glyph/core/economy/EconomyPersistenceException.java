package com.glyph.core.economy;

/**
 * Wraps checked {@link java.sql.SQLException}s from the economy repository so
 * callers compose cleanly with {@link java.util.concurrent.CompletableFuture}.
 */
public final class EconomyPersistenceException extends RuntimeException {

    public EconomyPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
