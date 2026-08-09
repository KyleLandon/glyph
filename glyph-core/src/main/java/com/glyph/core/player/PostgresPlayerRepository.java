package com.glyph.core.player;

import com.glyph.api.player.PlayerProfile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * JDBC implementation of {@link PlayerRepository} against the {@code players}
 * and {@code accounts} tables (migration V1).
 *
 * <p>All timestamps use the database clock ({@code now()}) so multiple backend
 * servers never disagree about time. The join upsert and the economy account
 * insert share one transaction: a player can never exist without an account.</p>
 */
public final class PostgresPlayerRepository implements PlayerRepository {

    /*
     * "(xmax = 0)" is a PostgreSQL system-column trick: xmax is zero for rows
     * created by this statement and non-zero for rows it updated, which tells
     * us whether this was a first join without a second round trip.
     */
    private static final String UPSERT_PLAYER = """
            INSERT INTO players (uuid, username)
            VALUES (?, ?)
            ON CONFLICT (uuid) DO UPDATE SET
                username   = EXCLUDED.username,
                last_join  = now(),
                last_seen  = now(),
                updated_at = now()
            RETURNING uuid, username, first_join, last_join, last_seen, playtime_seconds,
                      (xmax = 0) AS inserted
            """;

    /* RETURNING only yields a row when the insert actually happened, which
       tells us whether to ledger the starting balance. */
    private static final String ENSURE_ACCOUNT = """
            INSERT INTO accounts (id, owner_type, owner_uuid, balance, lifetime_earned)
            VALUES (?, 'PLAYER', ?, ?, ?)
            ON CONFLICT (owner_type, owner_uuid) DO NOTHING
            RETURNING id
            """;

    /* GDD section 122: every balance change corresponds to a transaction.
       A configured starting balance is a mint (null source). */
    private static final String LEDGER_STARTING_BALANCE = """
            INSERT INTO transactions (id, source_account, destination_account, amount, type, reason)
            VALUES (?, NULL, ?, ?, 'SYSTEM_REWARD', 'starting balance')
            """;

    private static final String RECORD_QUIT = """
            UPDATE players SET
                last_seen        = now(),
                playtime_seconds = playtime_seconds + ?,
                updated_at       = now()
            WHERE uuid = ?
            """;

    private static final String SELECT_COLUMNS =
            "SELECT uuid, username, first_join, last_join, last_seen, playtime_seconds FROM players ";

    /*
     * Supplier because the plugin constructs the repository before the pool
     * finishes async initialization; PlayerService gates every call on
     * database readiness, so the supplier only resolves once a pool exists.
     */
    private final Supplier<DataSource> dataSource;
    private final long startingBalance;

    public PostgresPlayerRepository(Supplier<DataSource> dataSource, long startingBalance) {
        this.dataSource = dataSource;
        this.startingBalance = Math.max(0, startingBalance);
    }

    @Override
    public JoinResult recordJoin(UUID uuid, String username) {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                PlayerProfile profile;
                boolean firstJoin;
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_PLAYER)) {
                    statement.setObject(1, uuid);
                    statement.setString(2, username);
                    try (ResultSet row = statement.executeQuery()) {
                        row.next();
                        profile = readProfile(row);
                        firstJoin = row.getBoolean("inserted");
                    }
                }
                // Idempotent: also heals a missing account for existing players.
                UUID createdAccountId = null;
                try (PreparedStatement statement = connection.prepareStatement(ENSURE_ACCOUNT)) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, uuid);
                    statement.setLong(3, startingBalance);
                    statement.setLong(4, startingBalance);
                    try (ResultSet created = statement.executeQuery()) {
                        if (created.next()) {
                            createdAccountId = created.getObject(1, UUID.class);
                        }
                    }
                }
                if (createdAccountId != null && startingBalance > 0) {
                    try (PreparedStatement statement =
                                 connection.prepareStatement(LEDGER_STARTING_BALANCE)) {
                        statement.setObject(1, UUID.randomUUID());
                        statement.setObject(2, createdAccountId);
                        statement.setLong(3, startingBalance);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return new JoinResult(profile, firstJoin);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new PlayerPersistenceException("recordJoin failed for " + uuid, e);
        }
    }

    @Override
    public void recordQuit(UUID uuid, long sessionSeconds) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(RECORD_QUIT)) {
            statement.setLong(1, Math.max(0, sessionSeconds));
            statement.setObject(2, uuid);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new PlayerPersistenceException("recordQuit failed for " + uuid, e);
        }
    }

    @Override
    public Optional<PlayerProfile> findByUuid(UUID uuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT_COLUMNS + "WHERE uuid = ?")) {
            statement.setObject(1, uuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readProfile(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PlayerPersistenceException("findByUuid failed for " + uuid, e);
        }
    }

    @Override
    public Optional<PlayerProfile> findByUsername(String username) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT_COLUMNS + "WHERE lower(username) = lower(?) ORDER BY last_seen DESC LIMIT 1")) {
            statement.setString(1, username);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readProfile(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PlayerPersistenceException("findByUsername failed for " + username, e);
        }
    }

    private static PlayerProfile readProfile(ResultSet row) throws SQLException {
        return new PlayerProfile(
                row.getObject("uuid", UUID.class),
                row.getString("username"),
                row.getObject("first_join", java.time.OffsetDateTime.class).toInstant(),
                row.getObject("last_join", java.time.OffsetDateTime.class).toInstant(),
                row.getObject("last_seen", java.time.OffsetDateTime.class).toInstant(),
                row.getLong("playtime_seconds"));
    }
}
