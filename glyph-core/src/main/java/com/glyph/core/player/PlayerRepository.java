package com.glyph.core.player;

import com.glyph.api.player.PlayerProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence operations for player identity.
 *
 * <p>Implementations are <b>blocking</b>; callers must dispatch to an async
 * executor ({@link PlayerService} does). This keeps JDBC code straightforward
 * while the service layer guarantees no tick thread ever waits on it.</p>
 */
public interface PlayerRepository {

    /**
     * Result of a join upsert.
     *
     * @param profile   the row as it exists after the upsert
     * @param firstJoin {@code true} when this join created the player
     */
    record JoinResult(PlayerProfile profile, boolean firstJoin) { }

    /**
     * Records a join: inserts the player on first join (also creating their
     * economy account, GDD section 100) or updates username, last_join and
     * last_seen on returning joins. Runs in a single database transaction.
     */
    JoinResult recordJoin(UUID uuid, String username);

    /**
     * Records a quit: stamps last_seen with the database clock and adds the
     * completed session's duration to accumulated playtime.
     */
    void recordQuit(UUID uuid, long sessionSeconds);

    Optional<PlayerProfile> findByUuid(UUID uuid);

    /** Case-insensitive exact-name lookup; most recently seen holder wins. */
    Optional<PlayerProfile> findByUsername(String username);
}
