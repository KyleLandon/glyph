package com.glyph.core.economy;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.api.economy.TopBalance;
import com.glyph.api.economy.TransactionType;
import com.glyph.api.economy.TransferResult;
import com.glyph.core.config.DatabaseSettings;
import com.glyph.core.database.DatabaseManager;
import com.glyph.core.economy.EconomyRepository.MutationOutcome;
import com.glyph.core.player.PostgresPlayerRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for atomic transfers against real PostgreSQL, including
 * the GDD-mandated concurrent payment test (sections 84, 134). Skipped when
 * Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresEconomyRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("glyph_test")
                    .withUsername("glyph_test")
                    .withPassword("glyph_test");

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static DatabaseManager manager;
    private static PostgresPlayerRepository playerRepository;
    private static PostgresEconomyRepository repository;

    @BeforeAll
    static void initDatabase() {
        manager = new DatabaseManager(
                new DatabaseSettings(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        2, 8, 5_000, 60_000, 300_000),
                LoggerFactory.getLogger("test"), EXECUTOR);
        manager.initAsync().join();
        playerRepository = new PostgresPlayerRepository(manager::dataSource, 0);
        repository = new PostgresEconomyRepository(manager::dataSource);
    }

    @AfterAll
    static void shutdown() {
        if (manager != null) {
            manager.close();
        }
        EXECUTOR.shutdownNow();
    }

    /** Creates a player + account (the Phase 2 join flow) and funds it. */
    private static UUID playerWithBalance(long balanceMinor) {
        UUID uuid = UUID.randomUUID();
        playerRepository.recordJoin(uuid, "P" + uuid.toString().substring(0, 8));
        if (balanceMinor > 0) {
            repository.adminAdjust(uuid, AdminOperation.SET, balanceMinor, null);
        }
        return uuid;
    }

    @Test
    void successfulTransferMovesMoneyAndWritesLedger() {
        UUID alice = playerWithBalance(10_00);
        UUID bob = playerWithBalance(0);

        MutationOutcome outcome = repository.transfer(alice, bob, 4_00, null);

        assertThat(outcome.result().isSuccess()).isTrue();
        assertThat(outcome.sourceBalanceAfter()).isEqualTo(6_00);
        assertThat(outcome.destBalanceAfter()).isEqualTo(4_00);
        assertThat(repository.balanceMinor(alice)).contains(6_00L);
        assertThat(repository.balanceMinor(bob)).contains(4_00L);

        List<LedgerEntry> history = repository.history(alice, 10);
        assertThat(history).isNotEmpty();
        LedgerEntry entry = history.getFirst();
        assertThat(entry.type()).isEqualTo(TransactionType.PLAYER_TRANSFER);
        assertThat(entry.amount().minorUnits()).isEqualTo(4_00);
        assertThat(entry.sourceOwner()).contains(alice);
        assertThat(entry.destOwner()).contains(bob);
    }

    @Test
    void insufficientFundsChangesNothing() {
        UUID alice = playerWithBalance(1_00);
        UUID bob = playerWithBalance(0);

        MutationOutcome outcome = repository.transfer(alice, bob, 5_00, null);

        assertThat(outcome.result().status())
                .isEqualTo(TransferResult.Status.INSUFFICIENT_FUNDS);
        assertThat(repository.balanceMinor(alice)).contains(1_00L);
        assertThat(repository.balanceMinor(bob)).contains(0L);
    }

    @Test
    void unknownAccountFails() {
        UUID alice = playerWithBalance(10_00);

        MutationOutcome outcome = repository.transfer(alice, UUID.randomUUID(), 1_00, null);

        assertThat(outcome.result().status())
                .isEqualTo(TransferResult.Status.ACCOUNT_NOT_FOUND);
        assertThat(repository.balanceMinor(alice)).contains(10_00L);
    }

    @Test
    void duplicateIdempotencyKeyRejectedAndTransfersOnce() {
        UUID alice = playerWithBalance(10_00);
        UUID bob = playerWithBalance(0);
        String key = "test-idem-" + UUID.randomUUID();

        MutationOutcome first = repository.transfer(alice, bob, 2_00, key);
        MutationOutcome second = repository.transfer(alice, bob, 2_00, key);

        assertThat(first.result().isSuccess()).isTrue();
        assertThat(second.result().status())
                .isEqualTo(TransferResult.Status.DUPLICATE_REQUEST);
        assertThat(repository.balanceMinor(alice)).contains(8_00L);
        assertThat(repository.balanceMinor(bob)).contains(2_00L);
    }

    /**
     * GDD sections 84/134: simultaneous transfers from one account. With
     * $10.00 and twenty concurrent $2.00 payments, exactly five must succeed
     * and money must be conserved.
     */
    @Test
    void concurrentTransfersNeverOverdraw() throws Exception {
        UUID payer = playerWithBalance(10_00);
        UUID payee = playerWithBalance(0);
        int attempts = 20;

        List<Future<MutationOutcome>> futures = new java.util.ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return repository.transfer(payer, payee, 2_00, null);
                }));
            }
            start.countDown();
        }

        long successes = 0;
        for (Future<MutationOutcome> future : futures) {
            if (future.get().result().isSuccess()) {
                successes++;
            }
        }

        assertThat(successes).isEqualTo(5);
        assertThat(repository.balanceMinor(payer)).contains(0L);
        assertThat(repository.balanceMinor(payee)).contains(10_00L);
    }

    /** Opposing concurrent transfers must not deadlock (ordered locking). */
    @Test
    void opposingTransfersDoNotDeadlock() throws Exception {
        UUID alice = playerWithBalance(100_00);
        UUID bob = playerWithBalance(100_00);
        int rounds = 30;

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<MutationOutcome>> futures = pool.invokeAll(
                    java.util.stream.IntStream.range(0, rounds).<java.util.concurrent.Callable<MutationOutcome>>mapToObj(
                            i -> () -> ThreadLocalRandom.current().nextBoolean()
                                    ? repository.transfer(alice, bob, 1_00, null)
                                    : repository.transfer(bob, alice, 1_00, null)).toList());
            for (Future<MutationOutcome> future : futures) {
                future.get(); // would throw on deadlock/timeout
            }
        }

        long total = repository.balanceMinor(alice).orElseThrow()
                + repository.balanceMinor(bob).orElseThrow();
        assertThat(total).isEqualTo(200_00L);
    }

    @Test
    void adminAdjustmentsAreLedgeredWithActor() throws Exception {
        UUID alice = playerWithBalance(0);
        UUID admin = UUID.randomUUID();

        MutationOutcome add = repository.adminAdjust(alice, AdminOperation.ADD, 50_00, admin);
        MutationOutcome remove = repository.adminAdjust(alice, AdminOperation.REMOVE, 20_00, admin);

        assertThat(add.result().isSuccess()).isTrue();
        assertThat(remove.result().isSuccess()).isTrue();
        assertThat(repository.balanceMinor(alice)).contains(30_00L);

        List<LedgerEntry> history = repository.history(alice, 10);
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        assertThat(history).allSatisfy(entry ->
                assertThat(entry.type()).isEqualTo(TransactionType.ADMIN_ADJUSTMENT));

        try (Connection connection = manager.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM transactions WHERE actor_uuid = ?")) {
            statement.setObject(1, admin);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                assertThat(row.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void removingMoreThanBalanceFails() {
        UUID alice = playerWithBalance(1_00);

        MutationOutcome outcome = repository.adminAdjust(
                alice, AdminOperation.REMOVE, 5_00, null);

        assertThat(outcome.result().status())
                .isEqualTo(TransferResult.Status.INSUFFICIENT_FUNDS);
        assertThat(repository.balanceMinor(alice)).contains(1_00L);
    }

    @Test
    void setToSameValueIsNoOpWithoutLedgerEntry() {
        UUID alice = playerWithBalance(7_00);
        int before = repository.history(alice, 50).size();

        MutationOutcome outcome = repository.adminAdjust(alice, AdminOperation.SET, 7_00, null);

        assertThat(outcome.result().isSuccess()).isTrue();
        assertThat(repository.history(alice, 50)).hasSize(before);
    }

    @Test
    void externalAdjustmentsLedgerAsSystemTypes() {
        UUID alice = playerWithBalance(0);

        repository.externalAdjust(alice, AdminOperation.ADD, 10_00,
                TransactionType.SYSTEM_REWARD, "vault bridge");
        repository.externalAdjust(alice, AdminOperation.REMOVE, 3_00,
                TransactionType.SYSTEM_SINK, "vault bridge");

        assertThat(repository.balanceMinor(alice)).contains(7_00L);
        List<LedgerEntry> history = repository.history(alice, 10);
        assertThat(history).extracting(LedgerEntry::type)
                .contains(TransactionType.SYSTEM_REWARD, TransactionType.SYSTEM_SINK);
        assertThat(history).extracting(LedgerEntry::reason).contains("vault bridge");
    }

    @Test
    void ensureAccountCreatesOnceWithZeroBalance() {
        UUID ghost = UUID.randomUUID(); // never joined

        assertThat(repository.ensureAccount(ghost)).isTrue();
        assertThat(repository.ensureAccount(ghost)).isTrue();
        assertThat(repository.balanceMinor(ghost)).contains(0L);
    }

    @Test
    void topBalancesOrderedDescending() {
        UUID rich = playerWithBalance(1_000_00);
        UUID richer = playerWithBalance(2_000_00);

        List<TopBalance> top = repository.topBalances(50);

        int richIndex = indexOf(top, rich);
        int richerIndex = indexOf(top, richer);
        assertThat(richerIndex).isLessThan(richIndex);
        for (int i = 1; i < top.size(); i++) {
            assertThat(top.get(i - 1).balance().minorUnits())
                    .isGreaterThanOrEqualTo(top.get(i).balance().minorUnits());
        }
    }

    private static int indexOf(List<TopBalance> top, UUID uuid) {
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).playerUuid().equals(uuid)) {
                return i;
            }
        }
        throw new AssertionError("player missing from baltop: " + uuid);
    }
}
