package com.glyph.discord.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Shared Postgres identity tables owned by GlyphCore migrations. */
public final class DiscordIdentityRepository {

    private final DataSource dataSource;

    public DiscordIdentityRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public Optional<UUID> findValidCode(String code, Instant now) throws SQLException {
        String sql = """
                SELECT minecraft_uuid
                FROM discord_link_codes
                WHERE code = ?
                  AND consumed_at IS NULL
                  AND expires_at > ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code.trim().toUpperCase());
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getObject(1, UUID.class));
            }
        }
    }

    /**
     * Consumes the code and upserts the link in one transaction.
     *
     * @return empty when the code is invalid/expired/already used
     */
    public Optional<UUID> consumeCodeAndLink(String code, long discordUserId, Instant now)
            throws SQLException {
        String consume = """
                UPDATE discord_link_codes
                SET consumed_at = ?
                WHERE code = ?
                  AND consumed_at IS NULL
                  AND expires_at > ?
                RETURNING minecraft_uuid
                """;
        String upsert = """
                INSERT INTO discord_links (minecraft_uuid, discord_user_id, linked_at, verified)
                VALUES (?, ?, now(), TRUE)
                ON CONFLICT (minecraft_uuid) DO UPDATE SET
                    discord_user_id = EXCLUDED.discord_user_id,
                    linked_at = now(),
                    verified = TRUE
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID minecraftUuid;
                try (PreparedStatement statement = connection.prepareStatement(consume)) {
                    Timestamp nowTs = Timestamp.from(now);
                    statement.setTimestamp(1, nowTs);
                    statement.setString(2, code.trim().toUpperCase());
                    statement.setTimestamp(3, nowTs);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return Optional.empty();
                        }
                        minecraftUuid = rs.getObject(1, UUID.class);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                    statement.setObject(1, minecraftUuid);
                    statement.setLong(2, discordUserId);
                    statement.executeUpdate();
                }
                connection.commit();
                return Optional.of(minecraftUuid);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public Optional<LinkedAccount> findByDiscord(long discordUserId) throws SQLException {
        String sql = """
                SELECT minecraft_uuid, discord_user_id, verified
                FROM discord_links
                WHERE discord_user_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discordUserId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LinkedAccount(
                        rs.getObject("minecraft_uuid", UUID.class),
                        rs.getLong("discord_user_id"),
                        rs.getBoolean("verified")));
            }
        }
    }

    public Optional<LinkedAccount> findByMinecraft(UUID minecraftUuid) throws SQLException {
        String sql = """
                SELECT minecraft_uuid, discord_user_id, verified
                FROM discord_links
                WHERE minecraft_uuid = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LinkedAccount(
                        rs.getObject("minecraft_uuid", UUID.class),
                        rs.getLong("discord_user_id"),
                        rs.getBoolean("verified")));
            }
        }
    }

    public void upsertLink(UUID minecraftUuid, long discordUserId) throws SQLException {
        String sql = """
                INSERT INTO discord_links (minecraft_uuid, discord_user_id, linked_at, verified)
                VALUES (?, ?, now(), TRUE)
                ON CONFLICT (minecraft_uuid) DO UPDATE SET
                    discord_user_id = EXCLUDED.discord_user_id,
                    linked_at = now(),
                    verified = TRUE
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            statement.setLong(2, discordUserId);
            statement.executeUpdate();
        }
    }

    public Optional<String> username(UUID minecraftUuid) throws SQLException {
        String sql = "SELECT username FROM players WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString(1));
            }
        }
    }

    public List<LinkedAccount> findAllLinked() throws SQLException {
        String sql = """
                SELECT minecraft_uuid, discord_user_id, verified
                FROM discord_links
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<LinkedAccount> links = new ArrayList<>();
            while (rs.next()) {
                links.add(new LinkedAccount(
                        rs.getObject("minecraft_uuid", UUID.class),
                        rs.getLong("discord_user_id"),
                        rs.getBoolean("verified")));
            }
            return links;
        }
    }

    public List<String> titleUnlocks(UUID minecraftUuid) throws SQLException {
        String sql = """
                SELECT product_id
                FROM glyph_unlocks
                WHERE player_uuid = ?
                  AND product_id LIKE 'title_%'
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet rs = statement.executeQuery()) {
                List<String> unlocks = new ArrayList<>();
                while (rs.next()) {
                    unlocks.add(rs.getString(1));
                }
                return unlocks;
            }
        }
    }

    public long lifetimeGlyphsEarned(UUID minecraftUuid) throws SQLException {
        String sql = """
                SELECT glyphs_lifetime_earned
                FROM accounts
                WHERE owner_type = 'PLAYER' AND owner_uuid = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                return rs.getLong(1);
            }
        }
    }

    public void setAlphaAccess(UUID minecraftUuid, boolean alpha) throws SQLException {
        String sql = """
                INSERT INTO player_access (minecraft_uuid, alpha, updated_at)
                VALUES (?, ?, now())
                ON CONFLICT (minecraft_uuid) DO UPDATE SET
                    alpha = EXCLUDED.alpha,
                    updated_at = now()
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            statement.setBoolean(2, alpha);
            statement.executeUpdate();
        }
    }

    public boolean hasAlphaAccess(UUID minecraftUuid) throws SQLException {
        String sql = "SELECT alpha FROM player_access WHERE minecraft_uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    public record LinkedAccount(UUID minecraftUuid, long discordUserId, boolean verified) {
    }
}
