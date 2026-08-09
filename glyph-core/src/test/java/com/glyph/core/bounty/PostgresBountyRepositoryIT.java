package com.glyph.core.bounty;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.core.bounty.BountyRepository.KillOutcome;
import com.glyph.core.bounty.BountyRepository.PlaceResult;
import com.glyph.core.bounty.BountyRepository.PlaceStatus;
import com.glyph.core.bounty.BountyRepository.TargetTotal;
import com.glyph.core.config.DatabaseSettings;
import com.glyph.core.database.DatabaseManager;
import com.glyph.core.economy.PostgresEconomyRepository;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bounty escrow and payout tests against real PostgreSQL (GDD sections 25,
 * 103): escrow moves with placement, payout is atomic with the kill record,
 * and concurrent kills never double-pay. Skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresBountyRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("glyph_test")
                    .withUsername("glyph_test")
                    .withPassword("glyph_test");

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private static DatabaseManager manager;
    private static PostgresPlayerRepository players;
    private static PostgresEconomyRepository economy;
    private static PostgresBountyRepository bounties;

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
        players = new PostgresPlayerRepository(manager::dataSource, 0);
        economy = new PostgresEconomyRepository(manager::dataSource);
        bounties = new PostgresBountyRepository(manager::dataSource);
    }

    @AfterAll
    static void shutdown() {
        if (manager != null) {
            manager.close();
        }
        EXECUTOR.shutdownNow();
    }

    private static UUID playerWithBalance(long balance) {
        UUID uuid = UUID.randomUUID();
        players.recordJoin(uuid, "P" + uuid.toString().substring(0, 8));
        if (balance > 0) {
            economy.adminAdjust(uuid, AdminOperation.SET, balance, null);
        }
        return uuid;
    }

    private static long escrowBalance() {
        try (Connection connection = manager.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT balance FROM accounts WHERE id = ?")) {
            statement.setObject(1, PostgresBountyRepository.ESCROW_ACCOUNT_ID);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static KillOutcome kill(UUID killer, UUID victim, int cooldownMinutes) {
        return bounties.recordKill(killer, victim, "world", 0, 64, 0,
                "{\"material\":\"DIAMOND_SWORD\"}", "ENTITY_ATTACK", cooldownMinutes);
    }

    @Test
    void placementEscrowsMoneyAtomically() {
        UUID creator = playerWithBalance(500_00);
        UUID target = playerWithBalance(0);
        long escrowBefore = escrowBalance();

        PlaceResult result = bounties.place(target, creator, 200_00);

        assertThat(result.status()).isEqualTo(PlaceStatus.SUCCESS);
        assertThat(result.creatorBalanceAfter()).isEqualTo(300_00);
        assertThat(economy.balance(creator)).contains(300_00L);
        assertThat(escrowBalance()).isEqualTo(escrowBefore + 200_00);
        assertThat(bounties.activeTotal(target)).isEqualTo(200_00);
    }

    @Test
    void placementWithoutFundsChangesNothing() {
        UUID creator = playerWithBalance(1_00);
        UUID target = playerWithBalance(0);
        long escrowBefore = escrowBalance();

        PlaceResult result = bounties.place(target, creator, 200_00);

        assertThat(result.status()).isEqualTo(PlaceStatus.INSUFFICIENT_FUNDS);
        assertThat(economy.balance(creator)).contains(1_00L);
        assertThat(escrowBalance()).isEqualTo(escrowBefore);
        assertThat(bounties.activeTotal(target)).isZero();
    }

    @Test
    void killPaysAllActiveBountiesAndRecordsKill() {
        UUID creatorA = playerWithBalance(100_00);
        UUID creatorB = playerWithBalance(100_00);
        UUID victim = playerWithBalance(0);
        UUID killer = playerWithBalance(0);
        bounties.place(victim, creatorA, 60_00);
        bounties.place(victim, creatorB, 40_00);
        long escrowBefore = escrowBalance();

        KillOutcome outcome = kill(killer, victim, 60);

        assertThat(outcome.bountyPaid()).isEqualTo(100_00);
        assertThat(outcome.bountiesClaimed()).isEqualTo(2);
        assertThat(outcome.withheld()).isFalse();
        assertThat(economy.balance(killer)).contains(100_00L);
        assertThat(escrowBalance()).isEqualTo(escrowBefore - 100_00);
        assertThat(bounties.activeTotal(victim)).isZero();

        // The kill row carries the paid amount (GDD 33).
        try (Connection connection = manager.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT bounty_amount, cause FROM player_kills WHERE killer_uuid = ?")) {
            statement.setObject(1, killer);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("bounty_amount")).isEqualTo(100_00);
                assertThat(row.getString("cause")).isEqualTo("ENTITY_ATTACK");
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void killWithoutBountyStillRecorded() {
        UUID victim = playerWithBalance(0);
        UUID killer = playerWithBalance(0);

        KillOutcome outcome = kill(killer, victim, 60);

        assertThat(outcome.bountyPaid()).isZero();
        assertThat(outcome.withheld()).isFalse();
    }

    /** Anti-farming (GDD 25): the second same-victim kill pays nothing. */
    @Test
    void repeatKillWithinCooldownWithholdsPayout() {
        UUID creator = playerWithBalance(200_00);
        UUID victim = playerWithBalance(0);
        UUID killer = playerWithBalance(0);

        KillOutcome first = kill(killer, victim, 60);
        assertThat(first.withheld()).isFalse();

        bounties.place(victim, creator, 50_00);
        KillOutcome second = kill(killer, victim, 60);

        assertThat(second.bountyPaid()).isZero();
        assertThat(second.withheld()).isTrue();
        // The bounty survives for someone else to claim.
        assertThat(bounties.activeTotal(victim)).isEqualTo(50_00);
        assertThat(economy.balance(killer)).contains(0L);

        // A different killer collects it fine.
        UUID other = playerWithBalance(0);
        KillOutcome fresh = kill(other, victim, 60);
        assertThat(fresh.bountyPaid()).isEqualTo(50_00);
    }

    /** Two concurrent kills of the same target: the bounty pays exactly once. */
    @Test
    void concurrentKillsNeverDoublePay() throws Exception {
        UUID creator = playerWithBalance(500_00);
        UUID victim = playerWithBalance(0);
        bounties.place(victim, creator, 300_00);
        int killers = 6;

        List<UUID> killerIds = new java.util.ArrayList<>();
        for (int i = 0; i < killers; i++) {
            killerIds.add(playerWithBalance(0));
        }

        List<Future<KillOutcome>> futures = new java.util.ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(killers)) {
            CountDownLatch start = new CountDownLatch(1);
            for (UUID killer : killerIds) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return kill(killer, victim, 60);
                }));
            }
            start.countDown();
        }

        long totalPaid = 0;
        for (Future<KillOutcome> future : futures) {
            totalPaid += future.get().bountyPaid();
        }
        assertThat(totalPaid).isEqualTo(300_00);
        assertThat(bounties.activeTotal(victim)).isZero();

        long killerBalances = killerIds.stream()
                .mapToLong(killer -> economy.balance(killer).orElseThrow())
                .sum();
        assertThat(killerBalances).isEqualTo(300_00);
    }

    @Test
    void topTargetsAggregatesAndOrders() {
        UUID creator = playerWithBalance(1_000_00);
        UUID wanted = playerWithBalance(0);
        UUID moreWanted = playerWithBalance(0);
        bounties.place(wanted, creator, 100_00);
        bounties.place(moreWanted, creator, 200_00);
        bounties.place(moreWanted, creator, 150_00);

        List<TargetTotal> top = bounties.topTargets(25);

        int wantedIdx = indexOf(top, wanted);
        int moreWantedIdx = indexOf(top, moreWanted);
        assertThat(moreWantedIdx).isLessThan(wantedIdx);
        assertThat(top.get(moreWantedIdx).total()).isEqualTo(350_00);
        assertThat(top.get(moreWantedIdx).count()).isEqualTo(2);
    }

    private static int indexOf(List<TargetTotal> top, UUID uuid) {
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).targetUuid().equals(uuid)) {
                return i;
            }
        }
        throw new AssertionError("target missing from top list: " + uuid);
    }
}
