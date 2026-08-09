package com.glyph.core.economy;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.api.economy.TopBalance;
import com.glyph.api.economy.TransferResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence operations for the economy. Implementations are <b>blocking</b>
 * (plain JDBC inside explicit transactions); {@link EconomyService} dispatches
 * every call to the async executor.
 */
public interface EconomyRepository {

    /**
     * Result of a successful mutation, carrying both parties' balances after
     * commit so the service can update HUDs without re-querying.
     *
     * @param result             API-facing outcome
     * @param sourceBalanceAfter payer balance in minor units, -1 if no source
     * @param destBalanceAfter   receiver balance in minor units, -1 if no destination
     */
    record MutationOutcome(TransferResult result, long sourceBalanceAfter, long destBalanceAfter) {

        public static MutationOutcome failure(TransferResult.Status status) {
            return new MutationOutcome(TransferResult.failure(status), -1, -1);
        }
    }

    /** @return the player's balance in minor units, empty if no account exists */
    Optional<Long> balanceMinor(UUID playerUuid);

    /**
     * Atomic transfer between two player accounts: one PostgreSQL transaction,
     * both rows locked with {@code SELECT ... FOR UPDATE} in deterministic
     * order, ledger entry inserted, then commit.
     */
    MutationOutcome transfer(UUID source, UUID destination, long amountMinor, String idempotencyKey);

    /**
     * Administrative set/add/remove. Mints (add/raise) and burns (remove/
     * lower) are ledgered as ADMIN_ADJUSTMENT with a null counter-account and
     * the acting admin in {@code actor_uuid}.
     */
    MutationOutcome adminAdjust(UUID playerUuid, AdminOperation operation, long amountMinor, UUID actor);

    List<TopBalance> topBalances(int limit);

    List<LedgerEntry> history(UUID playerUuid, int limit);
}
