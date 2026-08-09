package com.glyph.api.player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Read access to player identity data.
 *
 * <p>All lookups are asynchronous and never block a Minecraft tick thread.
 * Futures complete exceptionally if the database is unreachable; an empty
 * {@link Optional} means the player has never joined.</p>
 */
public interface PlayerApi {

    /**
     * Looks up a profile by account UUID. Online players are served from an
     * in-memory cache without touching the database.
     */
    CompletableFuture<Optional<PlayerProfile>> byUuid(UUID uuid);

    /**
     * Looks up a profile by exact username (case-insensitive). Usernames are
     * not unique over time; this returns the most recently seen holder.
     */
    CompletableFuture<Optional<PlayerProfile>> byUsername(String username);
}
