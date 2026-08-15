package com.glyph.core.discord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class PostgresDiscordLinkRepository implements DiscordLinkRepository {

    private final Supplier<DataSource> dataSource;

    public PostgresDiscordLinkRepository(Supplier<DataSource> dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Optional<LinkedAccount> findByMinecraft(UUID minecraftUuid) {
        String sql = """
                SELECT minecraft_uuid, discord_user_id, linked_at, verified
                FROM discord_links
                WHERE minecraft_uuid = ?
                """;
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DiscordLinkException("find link by minecraft failed for " + minecraftUuid, e);
        }
    }

    @Override
    public Optional<LinkedAccount> findByDiscord(long discordUserId) {
        String sql = """
                SELECT minecraft_uuid, discord_user_id, linked_at, verified
                FROM discord_links
                WHERE discord_user_id = ?
                """;
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discordUserId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DiscordLinkException("find link by discord failed for " + discordUserId, e);
        }
    }

    @Override
    public String issueCode(UUID minecraftUuid, Instant expiresAt) {
        String invalidate = """
                UPDATE discord_link_codes
                SET consumed_at = now()
                WHERE minecraft_uuid = ? AND consumed_at IS NULL
                """;
        String insert = """
                INSERT INTO discord_link_codes (code, minecraft_uuid, expires_at)
                VALUES (?, ?, ?)
                """;
        String code = DiscordLinkCodes.generate();
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(invalidate)) {
                    statement.setObject(1, minecraftUuid);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, code);
                    statement.setObject(2, minecraftUuid);
                    statement.setTimestamp(3, Timestamp.from(expiresAt));
                    statement.executeUpdate();
                }
                connection.commit();
                return code;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DiscordLinkException("issue link code failed for " + minecraftUuid, e);
        }
    }

    @Override
    public Optional<UUID> consumeCode(String code, Instant now) {
        String sql = """
                UPDATE discord_link_codes
                SET consumed_at = ?
                WHERE code = ?
                  AND consumed_at IS NULL
                  AND expires_at > ?
                RETURNING minecraft_uuid
                """;
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp nowTs = Timestamp.from(now);
            statement.setTimestamp(1, nowTs);
            statement.setString(2, DiscordLinkCodes.normalize(code));
            statement.setTimestamp(3, nowTs);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getObject(1, UUID.class));
            }
        } catch (SQLException e) {
            throw new DiscordLinkException("consume link code failed for " + code, e);
        }
    }

    @Override
    public void upsertLink(UUID minecraftUuid, long discordUserId) {
        String sql = """
                INSERT INTO discord_links (minecraft_uuid, discord_user_id, linked_at, verified)
                VALUES (?, ?, now(), TRUE)
                ON CONFLICT (minecraft_uuid) DO UPDATE SET
                    discord_user_id = EXCLUDED.discord_user_id,
                    linked_at = now(),
                    verified = TRUE
                """;
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            statement.setLong(2, discordUserId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DiscordLinkException(
                    "upsert discord link failed for " + minecraftUuid + " / " + discordUserId, e);
        }
    }

    @Override
    public boolean deleteLink(UUID minecraftUuid) {
        String sql = "DELETE FROM discord_links WHERE minecraft_uuid = ?";
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DiscordLinkException("delete discord link failed for " + minecraftUuid, e);
        }
    }

    @Override
    public boolean deleteLinkByDiscord(long discordUserId) {
        String sql = "DELETE FROM discord_links WHERE discord_user_id = ?";
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, discordUserId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DiscordLinkException("delete discord link failed for " + discordUserId, e);
        }
    }

    private static LinkedAccount map(ResultSet rs) throws SQLException {
        return new LinkedAccount(
                rs.getObject("minecraft_uuid", UUID.class),
                rs.getLong("discord_user_id"),
                rs.getTimestamp("linked_at").toInstant(),
                rs.getBoolean("verified"));
    }
}
