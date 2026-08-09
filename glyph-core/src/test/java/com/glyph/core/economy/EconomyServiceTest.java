package com.glyph.core.economy;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TopBalance;
import com.glyph.api.economy.TransferResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit tests with an in-memory repository and same-thread executor; the SQL
 * itself is covered by {@link PostgresEconomyRepositoryIT}.
 */
class EconomyServiceTest {

    private static final class InMemoryRepository implements EconomyRepository {
        final Map<UUID, Long> balances = new HashMap<>();
        boolean failNextCall;

        @Override
        public Optional<Long> balance(UUID playerUuid) {
            maybeFail();
            return Optional.ofNullable(balances.get(playerUuid));
        }

        @Override
        public MutationOutcome transfer(UUID source, UUID destination,
                                        long amount, String idempotencyKey) {
            maybeFail();
            Long sourceBalance = balances.get(source);
            Long destBalance = balances.get(destination);
            if (sourceBalance == null || destBalance == null) {
                return MutationOutcome.failure(TransferResult.Status.ACCOUNT_NOT_FOUND);
            }
            if (sourceBalance < amount) {
                return MutationOutcome.failure(TransferResult.Status.INSUFFICIENT_FUNDS);
            }
            balances.put(source, sourceBalance - amount);
            balances.put(destination, destBalance + amount);
            return new MutationOutcome(
                    TransferResult.success(UUID.randomUUID(),
                            Money.of(sourceBalance - amount)),
                    sourceBalance - amount, destBalance + amount);
        }

        @Override
        public MutationOutcome adminAdjust(UUID playerUuid, AdminOperation operation,
                                           long amount, UUID actor) {
            maybeFail();
            Long balance = balances.get(playerUuid);
            if (balance == null) {
                return MutationOutcome.failure(TransferResult.Status.ACCOUNT_NOT_FOUND);
            }
            long target = switch (operation) {
                case SET -> amount;
                case ADD -> balance + amount;
                case REMOVE -> balance - amount;
            };
            if (target < 0) {
                return MutationOutcome.failure(TransferResult.Status.INSUFFICIENT_FUNDS);
            }
            balances.put(playerUuid, target);
            return new MutationOutcome(
                    TransferResult.success(UUID.randomUUID(), Money.of(target)), target, -1);
        }

        @Override
        public MutationOutcome externalAdjust(UUID playerUuid, AdminOperation operation,
                                              long amount, com.glyph.api.economy.TransactionType type,
                                              String reason) {
            return adminAdjust(playerUuid, operation, amount, null);
        }

        @Override
        public boolean ensureAccount(UUID playerUuid) {
            maybeFail();
            balances.putIfAbsent(playerUuid, 0L);
            return true;
        }

        @Override
        public List<TopBalance> topBalances(int limit) {
            maybeFail();
            return List.of();
        }

        @Override
        public List<LedgerEntry> history(UUID playerUuid, int limit) {
            maybeFail();
            return List.of();
        }

        private void maybeFail() {
            if (failNextCall) {
                failNextCall = false;
                throw new EconomyPersistenceException("simulated outage",
                        new RuntimeException("connection refused"));
            }
        }
    }

    private final InMemoryRepository repository = new InMemoryRepository();
    private final AtomicBoolean databaseReady = new AtomicBoolean(true);
    private final EconomyService service = new EconomyService(
            repository, databaseReady::get, Runnable::run, LoggerFactory.getLogger("test"));

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void transferMovesMoneyAndNotifiesBothParties() {
        repository.balances.put(alice, 10_00L);
        repository.balances.put(bob, 0L);
        List<String> notifications = new ArrayList<>();
        service.addBalanceListener((uuid, balance) ->
                notifications.add(uuid + "=" + balance.dollars()));

        TransferResult result = service.transfer(alice, bob, Money.of(4_00), null).join();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.newBalance()).contains(Money.of(6_00));
        assertThat(repository.balances).containsEntry(alice, 6_00L).containsEntry(bob, 4_00L);
        assertThat(notifications).containsExactlyInAnyOrder(
                alice + "=600", bob + "=400");
    }

    @Test
    void selfPaymentRejectedBeforeTouchingRepository() {
        repository.balances.put(alice, 10_00L);
        repository.failNextCall = true; // would explode if the repo were hit

        TransferResult result = service.transfer(alice, alice, Money.of(1_00), null).join();

        assertThat(result.status()).isEqualTo(TransferResult.Status.SELF_PAYMENT);
        assertThat(repository.failNextCall).isTrue();
    }

    @Test
    void nullOrZeroAmountRejected() {
        assertThat(service.transfer(alice, bob, null, null).join().status())
                .isEqualTo(TransferResult.Status.INVALID_AMOUNT);
        assertThat(service.transfer(alice, bob, Money.ZERO, null).join().status())
                .isEqualTo(TransferResult.Status.INVALID_AMOUNT);
    }

    @Test
    void databaseDownFailsSoftly() {
        databaseReady.set(false);

        TransferResult transfer = service.transfer(alice, bob, Money.of(100), null).join();
        TransferResult admin = service.adminAdjust(
                alice, AdminOperation.ADD, Money.of(100), null).join();

        assertThat(transfer.status()).isEqualTo(TransferResult.Status.FAILED);
        assertThat(admin.status()).isEqualTo(TransferResult.Status.FAILED);
    }

    @Test
    void repositoryExceptionBecomesFailedResultNotThrow() {
        repository.balances.put(alice, 10_00L);
        repository.balances.put(bob, 0L);
        repository.failNextCall = true;

        TransferResult result = service.transfer(alice, bob, Money.of(100), null).join();

        assertThat(result.status()).isEqualTo(TransferResult.Status.FAILED);
    }

    @Test
    void adminSetToZeroIsAllowed() {
        repository.balances.put(alice, 10_00L);

        TransferResult result = service.adminAdjust(
                alice, AdminOperation.SET, Money.ZERO, null).join();

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.balances).containsEntry(alice, 0L);
    }

    @Test
    void adminAddOfZeroRejected() {
        repository.balances.put(alice, 10_00L);

        TransferResult result = service.adminAdjust(
                alice, AdminOperation.ADD, Money.ZERO, null).join();

        assertThat(result.status()).isEqualTo(TransferResult.Status.INVALID_AMOUNT);
    }

    @Test
    void failingBalanceListenerDoesNotBreakTransfer() {
        repository.balances.put(alice, 10_00L);
        repository.balances.put(bob, 0L);
        service.addBalanceListener((uuid, balance) -> {
            throw new RuntimeException("broken HUD");
        });

        TransferResult result = service.transfer(alice, bob, Money.of(100), null).join();

        assertThat(result.isSuccess()).isTrue();
    }
}
