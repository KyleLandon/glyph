package com.glyph.core.nick;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class PostgresNicknameRepository implements NicknameRepository {

    private static final String FIND = """
            SELECT nickname FROM player_nicknames
            WHERE player_uuid = ? AND market = ?
            """;

    private static final String FIND_OWNER = """
            SELECT player_uuid FROM player_nicknames
            WHERE market = ? AND lower(nickname) = lower(?)
            """;

    private static final String UPSERT = """
            INSERT INTO player_nicknames (player_uuid, market, nickname)
            VALUES (?, ?, ?)
            ON CONFLICT (player_uuid, market) DO UPDATE SET
                nickname = EXCLUDED.nickname,
                updated_at = now()
            """;

    private static final String DELETE = """
            DELETE FROM player_nicknames
            WHERE player_uuid = ? AND market = ?
            """;

    private final Supplier<DataSource> dataSource;
    private final String market;

    public PostgresNicknameRepository(Supplier<DataSource> dataSource, String market) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.market = Objects.requireNonNull(market, "market");
    }

    @Override
    public Optional<String> find(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, market);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rows.getString("nickname"));
            }
        } catch (SQLException e) {
            throw new NicknamePersistenceException("find nickname failed for " + playerUuid, e);
        }
    }

    @Override
    public Optional<UUID> findOwner(String nickname) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_OWNER)) {
            statement.setString(1, market);
            statement.setString(2, nickname);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(rows.getObject("player_uuid", UUID.class));
            }
        } catch (SQLException e) {
            throw new NicknamePersistenceException("find nickname owner failed", e);
        }
    }

    @Override
    public void upsert(UUID playerUuid, String nickname) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, market);
            statement.setString(3, nickname);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new NicknamePersistenceException("upsert nickname failed for " + playerUuid, e);
        }
    }

    @Override
    public boolean delete(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, market);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new NicknamePersistenceException("delete nickname failed for " + playerUuid, e);
        }
    }
}
