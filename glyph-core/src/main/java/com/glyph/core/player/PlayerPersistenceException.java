package com.glyph.core.player;

/**
 * Wraps checked {@link java.sql.SQLException}s from the repository so callers
 * compose cleanly with {@link java.util.concurrent.CompletableFuture}.
 */
public final class PlayerPersistenceException extends RuntimeException {

    public PlayerPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
