package com.glyph.core.bounty;

import com.glyph.api.economy.TransactionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * JDBC implementation of {@link BountyRepository}.
 *
 * <p>The fixed ESCROW account (created in migration V4) holds all bounty
 * money while bounties are ACTIVE: placement transfers creator → escrow,
 * payout transfers escrow → killer. Every movement is ledgered with the
 * bounty id as {@code related_entity}, so escrow always reconciles to the
 * sum of ACTIVE bounty amounts (GDD section 127).</p>
 */
public final class PostgresBountyRepository implements BountyRepository {

    /** The ESCROW account inserted by V4__bounties_and_kills.sql. */
    public static final UUID ESCROW_ACCOUNT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String LOCK_PLAYER_ACCOUNT = """
            SELECT id, balance FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid = ?
            FOR UPDATE
            """;

    private static final String LOCK_ESCROW_ACCOUNT =
            "SELECT id, balance FROM accounts WHERE id = ? FOR UPDATE";

    private static final String DEBIT = """
            UPDATE accounts SET
                balance        = balance - ?,
                lifetime_spent = lifetime_spent + ?,
                updated_at     = now()
            WHERE id = ?
            """;

    private static final String CREDIT = """
            UPDATE accounts SET
                balance         = balance + ?,
                lifetime_earned = lifetime_earned + ?,
                updated_at      = now()
            WHERE id = ?
            """;

    // Escrow is a holding account: its lifetime counters stay untouched so
    // they never pollute economy analytics.
    private static final String ESCROW_ADD =
            "UPDATE accounts SET balance = balance + ?, updated_at = now() WHERE id = ?";
    private static final String ESCROW_REMOVE =
            "UPDATE accounts SET balance = balance - ?, updated_at = now() WHERE id = ?";

    private static final String INSERT_LEDGER = """
            INSERT INTO transactions
                (id, source_account, destination_account, amount, type, reason,
                 related_entity, actor_uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_BOUNTY = """
            INSERT INTO bounties (id, target_uuid, creator_uuid, amount, status)
            VALUES (?, ?, ?, ?, 'ACTIVE')
            """;

    private static final String LOCK_ACTIVE_BOUNTIES = """
            SELECT id, amount FROM bounties
            WHERE target_uuid = ? AND status = 'ACTIVE'
            ORDER BY created_at
            FOR UPDATE
            """;

    private static final String CLAIM_BOUNTY = """
            UPDATE bounties
            SET status = 'CLAIMED', claimed_by = ?, claimed_at = now()
            WHERE id = ?
            """;

    private static final String RECENT_SAME_VICTIM_KILL = """
            SELECT 1 FROM player_kills
            WHERE killer_uuid = ? AND victim_uuid = ?
              AND created_at > now() - make_interval(mins => ?)
            LIMIT 1
            """;

    private static final String INSERT_KILL = """
            INSERT INTO player_kills
                (id, killer_uuid, victim_uuid, world, x, y, z, weapon, cause, bounty_amount)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """;

    private static final String ACTIVE_TOTAL =
            "SELECT coalesce(sum(amount), 0) FROM bounties"
                    + " WHERE target_uuid = ? AND status = 'ACTIVE'";

    private static final String TOP_TARGETS = """
            SELECT b.target_uuid, p.username, sum(b.amount) AS total, count(*) AS bounty_count
            FROM bounties b
            JOIN players p ON p.uuid = b.target_uuid
            WHERE b.status = 'ACTIVE'
            GROUP BY b.target_uuid, p.username
            ORDER BY total DESC
            LIMIT ?
            """;

    private final Supplier<DataSource> dataSource;

    public PostgresBountyRepository(Supplier<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    private record LockedAccount(UUID accountId, long balance) { }

    @Override
    public PlaceResult place(UUID targetUuid, UUID creatorUuid, long amountMinor) {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedAccount creator = lockPlayerAccount(connection, creatorUuid);
                if (creator == null) {
                    connection.rollback();
                    return PlaceResult.failure(PlaceStatus.ACCOUNT_NOT_FOUND);
                }
                if (creator.balance() < amountMinor) {
                    connection.rollback();
                    return PlaceResult.failure(PlaceStatus.INSUFFICIENT_FUNDS);
                }

                UUID bountyId = UUID.randomUUID();
                execute(connection, DEBIT, amountMinor, amountMinor, creator.accountId());
                executeEscrow(connection, ESCROW_ADD, amountMinor);
                try (PreparedStatement statement = connection.prepareStatement(INSERT_BOUNTY)) {
                    statement.setObject(1, bountyId);
                    statement.setObject(2, targetUuid);
                    statement.setObject(3, creatorUuid);
                    statement.setLong(4, amountMinor);
                    statement.executeUpdate();
                }
                ledger(connection, creator.accountId(), ESCROW_ACCOUNT_ID, amountMinor,
                        TransactionType.BOUNTY_ESCROW, "bounty placed", bountyId, creatorUuid);

                connection.commit();
                return new PlaceResult(PlaceStatus.SUCCESS, creator.balance() - amountMinor);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new BountyPersistenceException(
                    "bounty placement failed: " + creatorUuid + " on " + targetUuid, e);
        }
    }

    @Override
    public KillOutcome recordKill(UUID killerUuid, UUID victimUuid, String world,
                                  int x, int y, int z, String weaponJson, String cause,
                                  int sameVictimCooldownMinutes) {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean farmed = recentSameVictimKill(
                        connection, killerUuid, victimUuid, sameVictimCooldownMinutes);

                record LockedBounty(UUID id, long amount) { }
                List<LockedBounty> active = new ArrayList<>();
                try (PreparedStatement statement =
                             connection.prepareStatement(LOCK_ACTIVE_BOUNTIES)) {
                    statement.setObject(1, victimUuid);
                    try (ResultSet row = statement.executeQuery()) {
                        while (row.next()) {
                            active.add(new LockedBounty(
                                    row.getObject("id", UUID.class), row.getLong("amount")));
                        }
                    }
                }

                long paid = 0;
                int claimed = 0;
                boolean withheld = false;
                if (!active.isEmpty() && farmed) {
                    withheld = true;
                } else if (!active.isEmpty()) {
                    LockedAccount killer = lockPlayerAccount(connection, killerUuid);
                    if (killer != null) {
                        // Lock escrow after the player account: every writer
                        // orders player -> escrow, so no deadlock is possible.
                        executeEscrowLock(connection);
                        for (LockedBounty bounty : active) {
                            try (PreparedStatement statement =
                                         connection.prepareStatement(CLAIM_BOUNTY)) {
                                statement.setObject(1, killerUuid);
                                statement.setObject(2, bounty.id());
                                statement.executeUpdate();
                            }
                            ledger(connection, ESCROW_ACCOUNT_ID, killer.accountId(),
                                    bounty.amount(), TransactionType.BOUNTY_REWARD,
                                    "bounty claimed", bounty.id(), killerUuid);
                            paid += bounty.amount();
                            claimed++;
                        }
                        executeEscrow(connection, ESCROW_REMOVE, paid);
                        execute(connection, CREDIT, paid, paid, killer.accountId());
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(INSERT_KILL)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, killerUuid);
                    statement.setObject(3, victimUuid);
                    statement.setString(4, world);
                    statement.setInt(5, x);
                    statement.setInt(6, y);
                    statement.setInt(7, z);
                    statement.setString(8, weaponJson);
                    statement.setString(9, cause);
                    statement.setLong(10, paid);
                    statement.executeUpdate();
                }

                connection.commit();
                return new KillOutcome(paid, claimed, withheld);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new BountyPersistenceException(
                    "kill record failed: " + killerUuid + " -> " + victimUuid, e);
        }
    }

    @Override
    public long activeTotal(UUID targetUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(ACTIVE_TOTAL)) {
            statement.setObject(1, targetUuid);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        } catch (SQLException e) {
            throw new BountyPersistenceException("activeTotal failed for " + targetUuid, e);
        }
    }

    @Override
    public List<TargetTotal> topTargets(int limit) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(TOP_TARGETS)) {
            statement.setInt(1, limit);
            try (ResultSet row = statement.executeQuery()) {
                List<TargetTotal> top = new ArrayList<>();
                while (row.next()) {
                    top.add(new TargetTotal(
                            row.getObject("target_uuid", UUID.class),
                            row.getString("username"),
                            row.getLong("total"),
                            row.getInt("bounty_count")));
                }
                return top;
            }
        } catch (SQLException e) {
            throw new BountyPersistenceException("topTargets failed", e);
        }
    }

    private LockedAccount lockPlayerAccount(Connection connection, UUID ownerUuid)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_PLAYER_ACCOUNT)) {
            statement.setObject(1, ownerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? new LockedAccount(row.getObject("id", UUID.class), row.getLong("balance"))
                        : null;
            }
        }
    }

    private void executeEscrowLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ESCROW_ACCOUNT)) {
            statement.setObject(1, ESCROW_ACCOUNT_ID);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("escrow account missing: " + ESCROW_ACCOUNT_ID);
                }
            }
        }
    }

    private boolean recentSameVictimKill(Connection connection, UUID killer, UUID victim,
                                         int cooldownMinutes) throws SQLException {
        if (cooldownMinutes <= 0) {
            return false;
        }
        try (PreparedStatement statement =
                     connection.prepareStatement(RECENT_SAME_VICTIM_KILL)) {
            statement.setObject(1, killer);
            statement.setObject(2, victim);
            statement.setInt(3, cooldownMinutes);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    private static void execute(Connection connection, String sql, long a, long b, UUID id)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, a);
            statement.setLong(2, b);
            statement.setObject(3, id);
            statement.executeUpdate();
        }
    }

    private void executeEscrow(Connection connection, String sql, long amount)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setObject(2, ESCROW_ACCOUNT_ID);
            statement.executeUpdate();
        }
    }

    private static void ledger(Connection connection, UUID sourceAccount, UUID destAccount,
                               long amount, TransactionType type, String reason,
                               UUID relatedEntity, UUID actor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LEDGER)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, sourceAccount);
            statement.setObject(3, destAccount);
            statement.setLong(4, amount);
            statement.setString(5, type.name());
            statement.setString(6, reason);
            statement.setObject(7, relatedEntity);
            statement.setObject(8, actor);
            statement.executeUpdate();
        }
    }
}
