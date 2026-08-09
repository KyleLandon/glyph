package com.glyph.core.bounty;

import java.util.List;
import java.util.UUID;

/**
 * Persistence contract for bounties and the kill log (GDD sections 25, 33).
 *
 * <p>Escrow invariant: a bounty row exists if and only if its escrow
 * transfer committed (GDD 103: "Never create bounty before escrow
 * succeeds"). Both happen in one transaction here.</p>
 */
public interface BountyRepository {

    enum PlaceStatus { SUCCESS, INSUFFICIENT_FUNDS, ACCOUNT_NOT_FOUND, FAILED }

    record PlaceResult(PlaceStatus status, long creatorBalanceAfter) {

        public static PlaceResult failure(PlaceStatus status) {
            return new PlaceResult(status, -1);
        }
    }

    /** Aggregated active bounty on one player, for lists and lookups. */
    record TargetTotal(UUID targetUuid, String targetName, long total, int count) { }

    /**
     * @param bountyPaid total paid to the killer (0 when none active
     *                        or payout was withheld)
     * @param withheld        true when active bounties existed but the
     *                        same-victim cooldown blocked the payout
     */
    record KillOutcome(long bountyPaid, int bountiesClaimed, boolean withheld) { }

    /** Escrows {@code amount} (whole dollars) from the creator and creates the bounty. */
    PlaceResult place(UUID targetUuid, UUID creatorUuid, long amount);

    /**
     * Records a player kill (GDD 33) and pays out every ACTIVE bounty on the
     * victim from escrow — one transaction. If the killer already killed this
     * victim within {@code sameVictimCooldownMinutes}, the kill is still
     * recorded but the payout is withheld and bounties stay ACTIVE
     * (anti-farming, GDD 25).
     */
    KillOutcome recordKill(UUID killerUuid, UUID victimUuid, String world,
                           int x, int y, int z, String weaponJson, String cause,
                           int sameVictimCooldownMinutes);

    long activeTotal(UUID targetUuid);

    List<TargetTotal> topTargets(int limit);
}
