package com.glyph.api.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent identity of a player (GDD section 49, {@code players} table).
 *
 * <p>The UUID is authoritative; usernames are display data that update
 * automatically when Mojang reports a change. All timestamps come from the
 * database clock, not the game server.</p>
 *
 * @param uuid            Mojang account UUID (primary key)
 * @param username        last known username
 * @param firstJoin       when the player first joined the network
 * @param lastJoin        when the player most recently joined
 * @param lastSeen        last moment the player was known to be online
 * @param playtimeSeconds accumulated online time across all sessions
 */
public record PlayerProfile(
        UUID uuid,
        String username,
        Instant firstJoin,
        Instant lastJoin,
        Instant lastSeen,
        long playtimeSeconds) {

    public PlayerProfile {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(firstJoin, "firstJoin");
        Objects.requireNonNull(lastJoin, "lastJoin");
        Objects.requireNonNull(lastSeen, "lastSeen");
        if (playtimeSeconds < 0) {
            throw new IllegalArgumentException("playtimeSeconds must be >= 0");
        }
    }
}
