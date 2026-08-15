package com.glyph.core.discord;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DiscordLinkRepository {

    Optional<LinkedAccount> findByMinecraft(UUID minecraftUuid);

    Optional<LinkedAccount> findByDiscord(long discordUserId);

    /** Invalidates open codes for the player, then inserts a new one. */
    String issueCode(UUID minecraftUuid, Instant expiresAt);

    /**
     * Atomically consumes a valid unexpired code.
     *
     * @return minecraft UUID when consumed
     */
    Optional<UUID> consumeCode(String code, Instant now);

    void upsertLink(UUID minecraftUuid, long discordUserId);

    boolean deleteLink(UUID minecraftUuid);

    boolean deleteLinkByDiscord(long discordUserId);

    record LinkedAccount(UUID minecraftUuid, long discordUserId, Instant linkedAt, boolean verified) {
    }
}
