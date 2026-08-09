package com.glyph.core.economy;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TopBalance;
import com.glyph.api.economy.TransactionType;
import com.glyph.api.economy.TransferResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * JDBC implementation of {@link EconomyRepository} (GDD sections 19-20).
 *
 * <p>Correctness rules implemented here:</p>
 * <ul>
 *   <li>Every mutation is one explicit PostgreSQL transaction.</li>
 *   <li>Account rows are locked with {@code SELECT ... FOR UPDATE}; both
 *       transfer parties are locked in a single statement ordered by account
 *       id, so concurrent transfers cannot deadlock.</li>
 *   <li>A payment either fully happens (both balances + ledger row) or
 *       nothing happens — never a partial transfer.</li>
 *   <li>Duplicate idempotency keys are rejected by a partial unique index;
 *       the race between check and insert resolves to DUPLICATE_REQUEST via
 *       the 23505 unique-violation handler.</li>
 *   <li>Overflow on the receiving side aborts the transfer before any
 *       update runs.</li>
 * </ul>
 */
public final class PostgresEconomyRepository implements EconomyRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private static final String LOCK_BOTH_ACCOUNTS = """
            SELECT id, owner_uuid, balance FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid IN (?, ?)
            ORDER BY id
            FOR UPDATE
            """;

    private static final String LOCK_ONE_ACCOUNT = """
            SELECT id, owner_uuid, balance FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid = ?
            FOR UPDATE
            """;

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

    private static final String INSERT_LEDGER = """
            INSERT INTO transactions
                (id, source_account, destination_account, amount, type, reason,
                 actor_uuid, idempotency_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String IDEMPOTENCY_EXISTS =
            "SELECT 1 FROM transactions WHERE idempotency_key = ?";

    private static final String SELECT_BALANCE =
            "SELECT balance FROM accounts WHERE owner_type = 'PLAYER' AND owner_uuid = ?";

    private static final String TOP_BALANCES = """
            SELECT a.owner_uuid, p.username, a.balance
            FROM accounts a
            JOIN players p ON p.uuid = a.owner_uuid
            WHERE a.owner_type = 'PLAYER'
            ORDER BY a.balance DESC, p.username
            LIMIT ?
            """;

    private static final String HISTORY = """
            SELECT t.id, sa.owner_uuid AS source_owner, da.owner_uuid AS dest_owner,
                   t.amount, t.type, t.reason, t.created_at
            FROM transactions t
            LEFT JOIN accounts sa ON sa.id = t.source_account
            LEFT JOIN accounts da ON da.id = t.destination_account
            WHERE sa.owner_uuid = ? OR da.owner_uuid = ?
            ORDER BY t.created_at DESC, t.id
            LIMIT ?
            """;

    private final Supplier<DataSource> dataSource;

    public PostgresEconomyRepository(Supplier<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    private record LockedAccount(UUID accountId, long balance) { }

    @Override
    public MutationOutcome transfer(
            UUID source, UUID destination, long amountMinor, String idempotencyKey) {
        // Defense in depth: the service validates too, but a self-transfer
        // would silently corrupt lifetime counters if it reached the SQL.
        if (source.equals(destination)) {
            return MutationOutcome.failure(TransferResult.Status.SELF_PAYMENT);
        }
        if (amountMinor <= 0) {
            return MutationOutcome.failure(TransferResult.Status.INVALID_AMOUNT);
        }
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (idempotencyKey != null && idempotencyKeyUsed(connection, idempotencyKey)) {
                    connection.rollback();
                    return MutationOutcome.failure(TransferResult.Status.DUPLICATE_REQUEST);
                }

                LockedAccount sourceAccount = null;
                LockedAccount destAccount = null;
                try (PreparedStatement statement = connection.prepareStatement(LOCK_BOTH_ACCOUNTS)) {
                    statement.setObject(1, source);
                    statement.setObject(2, destination);
                    try (ResultSet row = statement.executeQuery()) {
                        while (row.next()) {
                            LockedAccount account = new LockedAccount(
                                    row.getObject("id", UUID.class), row.getLong("balance"));
                            UUID owner = row.getObject("owner_uuid", UUID.class);
                            if (owner.equals(source)) {
                                sourceAccount = account;
                            }
                            if (owner.equals(destination)) {
                                destAccount = account;
                            }
                        }
                    }
                }
                if (sourceAccount == null || destAccount == null) {
                    connection.rollback();
                    return MutationOutcome.failure(TransferResult.Status.ACCOUNT_NOT_FOUND);
                }
                if (sourceAccount.balance() < amountMinor) {
                    connection.rollback();
                    return MutationOutcome.failure(TransferResult.Status.INSUFFICIENT_FUNDS);
                }
                long destAfter;
                try {
                    destAfter = Math.addExact(destAccount.balance(), amountMinor);
                } catch (ArithmeticException overflow) {
                    connection.rollback();
                    return MutationOutcome.failure(TransferResult.Status.INVALID_AMOUNT);
                }

                executeUpdate(connection, DEBIT, amountMinor, amountMinor, sourceAccount.accountId());
                executeUpdate(connection, CREDIT, amountMinor, amountMinor, destAccount.accountId());

                UUID transactionId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement(INSERT_LEDGER)) {
                    statement.setObject(1, transactionId);
                    statement.setObject(2, sourceAccount.accountId());
                    statement.setObject(3, destAccount.accountId());
                    statement.setLong(4, amountMinor);
                    statement.setString(5, TransactionType.PLAYER_TRANSFER.name());
                    statement.setString(6, "player payment");
                    statement.setObject(7, source);
                    statement.setString(8, idempotencyKey);
                    statement.executeUpdate();
                }

                connection.commit();
                long sourceAfter = sourceAccount.balance() - amountMinor;
                return new MutationOutcome(
                        TransferResult.success(transactionId, Money.ofMinor(sourceAfter)),
                        sourceAfter, destAfter);
            } catch (SQLException e) {
                connection.rollback();
                if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    return MutationOutcome.failure(TransferResult.Status.DUPLICATE_REQUEST);
                }
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new EconomyPersistenceException(
                    "transfer failed: " + source + " -> " + destination, e);
        }
    }

    @Override
    public MutationOutcome adminAdjust(
            UUID playerUuid, AdminOperation operation, long amountMinor, UUID actor) {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedAccount account = null;
                try (PreparedStatement statement = connection.prepareStatement(LOCK_ONE_ACCOUNT)) {
                    statement.setObject(1, playerUuid);
                    try (ResultSet row = statement.executeQuery()) {
                        if (row.next()) {
                            account = new LockedAccount(
                                    row.getObject("id", UUID.class), row.getLong("balance"));
                        }
                    }
                }
                if (account == null) {
                    connection.rollback();
                    return MutationOutcome.failure(TransferResult.Status.ACCOUNT_NOT_FOUND);
                }

                long target = switch (operation) {
                    case SET -> amountMinor;
                    case ADD -> {
                        try {
                            yield Math.addExact(account.balance(), amountMinor);
                        } catch (ArithmeticException overflow) {
                            yield -1;
                        }
                    }
                    case REMOVE -> account.balance() - amountMinor;
                };
                if (target < 0) {
                    connection.rollback();
                    return MutationOutcome.failure(operation == AdminOperation.REMOVE
                            ? TransferResult.Status.INSUFFICIENT_FUNDS
                            : TransferResult.Status.INVALID_AMOUNT);
                }

                long delta = target - account.balance();
                if (delta == 0) {
                    connection.rollback();
                    return new MutationOutcome(
                            new TransferResult(TransferResult.Status.SUCCESS,
                                    Optional.empty(), Optional.of(Money.ofMinor(target))),
                            target, -1);
                }

                if (delta > 0) {
                    executeUpdate(connection, CREDIT, delta, delta, account.accountId());
                } else {
                    executeUpdate(connection, DEBIT, -delta, -delta, account.accountId());
                }

                UUID transactionId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement(INSERT_LEDGER)) {
                    statement.setObject(1, transactionId);
                    // Mint: money appears from nowhere (null source). Burn: it
                    // leaves circulation (null destination). GDD section 122.
                    statement.setObject(2, delta > 0 ? null : account.accountId());
                    statement.setObject(3, delta > 0 ? account.accountId() : null);
                    statement.setLong(4, Math.abs(delta));
                    statement.setString(5, TransactionType.ADMIN_ADJUSTMENT.name());
                    statement.setString(6, "eco " + operation.name().toLowerCase()
                            + " by " + (actor == null ? "console" : actor));
                    statement.setObject(7, actor);
                    statement.setString(8, null);
                    statement.executeUpdate();
                }

                connection.commit();
                return new MutationOutcome(
                        TransferResult.success(transactionId, Money.ofMinor(target)),
                        target, -1);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new EconomyPersistenceException("adminAdjust failed for " + playerUuid, e);
        }
    }

    @Override
    public Optional<Long> balanceMinor(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BALANCE)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(row.getLong(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new EconomyPersistenceException("balance lookup failed for " + playerUuid, e);
        }
    }

    @Override
    public List<TopBalance> topBalances(int limit) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(TOP_BALANCES)) {
            statement.setInt(1, limit);
            try (ResultSet row = statement.executeQuery()) {
                List<TopBalance> top = new ArrayList<>();
                while (row.next()) {
                    top.add(new TopBalance(
                            row.getObject("owner_uuid", UUID.class),
                            row.getString("username"),
                            Money.ofMinor(row.getLong("balance"))));
                }
                return top;
            }
        } catch (SQLException e) {
            throw new EconomyPersistenceException("topBalances failed", e);
        }
    }

    @Override
    public List<LedgerEntry> history(UUID playerUuid, int limit) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(HISTORY)) {
            statement.setObject(1, playerUuid);
            statement.setObject(2, playerUuid);
            statement.setInt(3, limit);
            try (ResultSet row = statement.executeQuery()) {
                List<LedgerEntry> entries = new ArrayList<>();
                while (entries.size() < limit && row.next()) {
                    entries.add(new LedgerEntry(
                            row.getObject("id", UUID.class),
                            Optional.ofNullable(row.getObject("source_owner", UUID.class)),
                            Optional.ofNullable(row.getObject("dest_owner", UUID.class)),
                            Money.ofMinor(row.getLong("amount")),
                            TransactionType.valueOf(row.getString("type")),
                            Optional.ofNullable(row.getString("reason")).orElse(""),
                            row.getObject("created_at", OffsetDateTime.class).toInstant()));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new EconomyPersistenceException("history failed for " + playerUuid, e);
        }
    }

    private boolean idempotencyKeyUsed(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(IDEMPOTENCY_EXISTS)) {
            statement.setString(1, key);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    private static void executeUpdate(
            Connection connection, String sql, long a, long b, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, a);
            statement.setLong(2, b);
            statement.setObject(3, id);
            statement.executeUpdate();
        }
    }
}
