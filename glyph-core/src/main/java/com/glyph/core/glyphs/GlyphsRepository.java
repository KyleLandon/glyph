package com.glyph.core.glyphs;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Persistence for Glyph balances, unlocks, and cosmetic state. */
public interface GlyphsRepository {

    long balance(UUID playerUuid);

    long lifetimeEarned(UUID playerUuid);

    /** @return new balance after credit */
    long credit(UUID playerUuid, long amount, String type, String reason, UUID actor);

    /**
     * @return new balance after debit, or empty when insufficient funds or no account
     */
    Optional<Long> debit(UUID playerUuid, long amount, String type, String reason, UUID actor);

    boolean hasUnlock(UUID playerUuid, String productId);

    void addUnlock(UUID playerUuid, String productId);

    Optional<String> nameColor(UUID playerUuid);

    void setNameColor(UUID playerUuid, String colorName);

    void clearNameColor(UUID playerUuid);

    Optional<String> equippedTitle(UUID playerUuid);

    void setEquippedTitle(UUID playerUuid, String titleUnlockId);

    void clearEquippedTitle(UUID playerUuid);

    Optional<String> deathStyle(UUID playerUuid);

    void setDeathStyle(UUID playerUuid, String productId);

    void clearDeathStyle(UUID playerUuid);

    boolean hudEnabled(UUID playerUuid);

    void setHudEnabled(UUID playerUuid, boolean enabled);

    List<String> unlocks(UUID playerUuid);

    /**
     * Records a first-time kill of {@code victimUuid} by {@code killerUuid}.
     *
     * @return the new unique-kill count when this call inserted a row
     */
    OptionalLong recordUniqueKill(UUID killerUuid, UUID victimUuid);

    long uniqueKillCount(UUID killerUuid);

    /** Increments bounty-claim counter; returns the new total. */
    long noteBountyClaim(UUID killerUuid);

    long ahSold(UUID sellerUuid);

    /** Adds auction sale proceeds; returns the new lifetime AH sold total. */
    long addAhSold(UUID sellerUuid, long salePriceDollars);

    /**
     * Records a one-time milestone if not already claimed.
     *
     * @return {@code true} when this call inserted the milestone row
     */
    boolean tryClaimMilestone(UUID playerUuid, String milestoneId);
}
