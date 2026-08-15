package com.glyph.core.glyphs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** JDBC implementation of {@link GlyphsRepository}. */
public final class PostgresGlyphsRepository implements GlyphsRepository {

    private static final String LOCK_ACCOUNT = """
            SELECT id, glyphs_balance FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid = ?
            FOR UPDATE
            """;

    private static final String CREDIT = """
            UPDATE accounts SET
                glyphs_balance         = glyphs_balance + ?,
                glyphs_lifetime_earned = glyphs_lifetime_earned + ?,
                updated_at             = now()
            WHERE id = ?
            """;

    private static final String DEBIT = """
            UPDATE accounts SET
                glyphs_balance        = glyphs_balance - ?,
                glyphs_lifetime_spent = glyphs_lifetime_spent + ?,
                updated_at            = now()
            WHERE id = ?
            """;

    private static final String INSERT_LEDGER = """
            INSERT INTO glyph_ledger (id, player_uuid, amount, direction, type, reason, actor_uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BALANCE = """
            SELECT glyphs_balance FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid = ?
            """;

    private static final String SELECT_LIFETIME_EARNED = """
            SELECT glyphs_lifetime_earned FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid = ?
            """;

    private static final String HAS_UNLOCK =
            "SELECT 1 FROM glyph_unlocks WHERE player_uuid = ? AND product_id = ?";

    private static final String ADD_UNLOCK = """
            INSERT INTO glyph_unlocks (player_uuid, product_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String SELECT_NAME_COLOR =
            "SELECT glyph_name_color FROM players WHERE uuid = ?";

    private static final String SET_NAME_COLOR =
            "UPDATE players SET glyph_name_color = ?, updated_at = now() WHERE uuid = ?";

    private static final String SELECT_EQUIPPED_TITLE =
            "SELECT glyph_equipped_title FROM players WHERE uuid = ?";

    private static final String SET_EQUIPPED_TITLE =
            "UPDATE players SET glyph_equipped_title = ?, updated_at = now() WHERE uuid = ?";

    private static final String SELECT_DEATH_STYLE =
            "SELECT glyph_death_style FROM players WHERE uuid = ?";

    private static final String SET_DEATH_STYLE =
            "UPDATE players SET glyph_death_style = ?, updated_at = now() WHERE uuid = ?";

    private static final String SELECT_HUD_ENABLED =
            "SELECT glyph_hud_enabled FROM players WHERE uuid = ?";

    private static final String SET_HUD_ENABLED =
            "UPDATE players SET glyph_hud_enabled = ?, updated_at = now() WHERE uuid = ?";

    private static final String SELECT_UNLOCKS =
            "SELECT product_id FROM glyph_unlocks WHERE player_uuid = ? ORDER BY product_id";

    private static final String INSERT_UNIQUE_KILL = """
            INSERT INTO glyph_unique_kills (killer_uuid, victim_uuid)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            RETURNING killer_uuid
            """;

    private static final String COUNT_UNIQUE_KILLS =
            "SELECT COUNT(*) FROM glyph_unique_kills WHERE killer_uuid = ?";

    private static final String NOTE_BOUNTY_CLAIM = """
            UPDATE players SET glyph_bounties_claimed = glyph_bounties_claimed + 1, updated_at = now()
            WHERE uuid = ?
            RETURNING glyph_bounties_claimed
            """;

    private static final String ADD_AH_SOLD = """
            UPDATE players SET glyph_ah_sold = glyph_ah_sold + ?, updated_at = now()
            WHERE uuid = ?
            RETURNING glyph_ah_sold
            """;

    private static final String CLAIM_MILESTONE = """
            INSERT INTO glyph_unlocks (player_uuid, product_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            RETURNING product_id
            """;

    private final Supplier<DataSource> dataSource;

    public PostgresGlyphsRepository(Supplier<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long balance(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BALANCE)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("balance lookup failed for " + playerUuid, e);
        }
    }

    @Override
    public long lifetimeEarned(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_LIFETIME_EARNED)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("lifetime earned lookup failed for " + playerUuid, e);
        }
    }

    @Override
    public long credit(UUID playerUuid, long amount, String type, String reason, UUID actor) {
        if (amount <= 0) {
            throw new IllegalArgumentException("credit amount must be positive");
        }
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedAccount account = lockAccountWithBalance(connection, playerUuid);
                if (account == null) {
                    connection.rollback();
                    return 0L;
                }
                executeUpdate(connection, CREDIT, amount, amount, account.accountId());
                insertLedger(connection, playerUuid, amount, "CREDIT", type, reason, actor);
                long newBalance = account.balance() + amount;
                connection.commit();
                return newBalance;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("credit failed for " + playerUuid, e);
        }
    }

    private record LockedAccount(UUID accountId, long balance) { }

    @Override
    public Optional<Long> debit(UUID playerUuid, long amount, String type, String reason, UUID actor) {
        if (amount <= 0) {
            throw new IllegalArgumentException("debit amount must be positive");
        }
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedAccount account = lockAccountWithBalance(connection, playerUuid);
                if (account == null) {
                    connection.rollback();
                    return Optional.empty();
                }
                if (account.balance() < amount) {
                    connection.rollback();
                    return Optional.empty();
                }
                executeUpdate(connection, DEBIT, amount, amount, account.accountId());
                insertLedger(connection, playerUuid, amount, "DEBIT", type, reason, actor);
                long newBalance = account.balance() - amount;
                connection.commit();
                return Optional.of(newBalance);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("debit failed for " + playerUuid, e);
        }
    }

    @Override
    public boolean hasUnlock(UUID playerUuid, String productId) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(HAS_UNLOCK)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, productId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException(
                    "unlock lookup failed for " + playerUuid + " / " + productId, e);
        }
    }

    @Override
    public void addUnlock(UUID playerUuid, String productId) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(ADD_UNLOCK)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, productId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new GlyphsPersistenceException(
                    "add unlock failed for " + playerUuid + " / " + productId, e);
        }
    }

    @Override
    public Optional<String> nameColor(UUID playerUuid) {
        return readOptionalString(SELECT_NAME_COLOR, playerUuid);
    }

    @Override
    public void setNameColor(UUID playerUuid, String colorName) {
        writeString(SET_NAME_COLOR, colorName, playerUuid);
    }

    @Override
    public void clearNameColor(UUID playerUuid) {
        writeString(SET_NAME_COLOR, null, playerUuid);
    }

    @Override
    public Optional<String> equippedTitle(UUID playerUuid) {
        return readOptionalString(SELECT_EQUIPPED_TITLE, playerUuid);
    }

    @Override
    public void setEquippedTitle(UUID playerUuid, String titleUnlockId) {
        writeString(SET_EQUIPPED_TITLE, titleUnlockId, playerUuid);
    }

    @Override
    public void clearEquippedTitle(UUID playerUuid) {
        writeString(SET_EQUIPPED_TITLE, null, playerUuid);
    }

    @Override
    public Optional<String> deathStyle(UUID playerUuid) {
        return readOptionalString(SELECT_DEATH_STYLE, playerUuid);
    }

    @Override
    public void setDeathStyle(UUID playerUuid, String productId) {
        writeString(SET_DEATH_STYLE, productId, playerUuid);
    }

    @Override
    public void clearDeathStyle(UUID playerUuid) {
        writeString(SET_DEATH_STYLE, null, playerUuid);
    }

    @Override
    public boolean hudEnabled(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_HUD_ENABLED)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() && row.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("hud lookup failed for " + playerUuid, e);
        }
    }

    @Override
    public void setHudEnabled(UUID playerUuid, boolean enabled) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(SET_HUD_ENABLED)) {
            statement.setBoolean(1, enabled);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("set hud failed for " + playerUuid, e);
        }
    }

    @Override
    public List<String> unlocks(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_UNLOCKS)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                List<String> unlocks = new ArrayList<>();
                while (row.next()) {
                    unlocks.add(row.getString(1));
                }
                return unlocks;
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("unlocks lookup failed for " + playerUuid, e);
        }
    }

    @Override
    public OptionalLong recordUniqueKill(UUID killerUuid, UUID victimUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement insert = connection.prepareStatement(INSERT_UNIQUE_KILL)) {
            insert.setObject(1, killerUuid);
            insert.setObject(2, victimUuid);
            try (ResultSet row = insert.executeQuery()) {
                if (!row.next()) {
                    return OptionalLong.empty();
                }
            }
            try (PreparedStatement count = connection.prepareStatement(COUNT_UNIQUE_KILLS)) {
                count.setObject(1, killerUuid);
                try (ResultSet row = count.executeQuery()) {
                    return row.next() ? OptionalLong.of(row.getLong(1)) : OptionalLong.empty();
                }
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException(
                    "record unique kill failed for " + killerUuid + " -> " + victimUuid, e);
        }
    }

    @Override
    public long uniqueKillCount(UUID killerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_UNIQUE_KILLS)) {
            statement.setObject(1, killerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("unique kill count failed for " + killerUuid, e);
        }
    }

    @Override
    public long noteBountyClaim(UUID killerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(NOTE_BOUNTY_CLAIM)) {
            statement.setObject(1, killerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("note bounty claim failed for " + killerUuid, e);
        }
    }

    @Override
    public long ahSold(UUID sellerUuid) {
        return readAhSold(sellerUuid);
    }

    @Override
    public long addAhSold(UUID sellerUuid, long salePriceDollars) {
        if (salePriceDollars <= 0) {
            return readAhSold(sellerUuid);
        }
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(ADD_AH_SOLD)) {
            statement.setLong(1, salePriceDollars);
            statement.setObject(2, sellerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("add ah sold failed for " + sellerUuid, e);
        }
    }

    private long readAhSold(UUID sellerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT glyph_ah_sold FROM players WHERE uuid = ?")) {
            statement.setObject(1, sellerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("ah sold lookup failed for " + sellerUuid, e);
        }
    }

    @Override
    public boolean tryClaimMilestone(UUID playerUuid, String milestoneId) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(CLAIM_MILESTONE)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, milestoneId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException(
                    "milestone claim failed for " + playerUuid + " / " + milestoneId, e);
        }
    }

    private Optional<String> readOptionalString(String sql, UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                String value = row.getString(1);
                return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
            }
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("lookup failed for " + playerUuid, e);
        }
    }

    private void writeString(String sql, String value, UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new GlyphsPersistenceException("write failed for " + playerUuid, e);
        }
    }

    private static LockedAccount lockAccountWithBalance(Connection connection, UUID playerUuid)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ACCOUNT)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? new LockedAccount(row.getObject("id", UUID.class), row.getLong("glyphs_balance"))
                        : null;
            }
        }
    }

    private static void insertLedger(
            Connection connection,
            UUID playerUuid,
            long amount,
            String direction,
            String type,
            String reason,
            UUID actor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LEDGER)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, playerUuid);
            statement.setLong(3, amount);
            statement.setString(4, direction);
            statement.setString(5, type);
            statement.setString(6, reason);
            statement.setObject(7, actor);
            statement.executeUpdate();
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
