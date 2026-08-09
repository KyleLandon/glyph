package com.glyph.core.delivery;

/** Wraps SQL failures from the delivery repository. */
public final class DeliveryPersistenceException extends RuntimeException {

    public DeliveryPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
