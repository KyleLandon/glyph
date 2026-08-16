package com.glyph.core.home;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class PostgresHomeRepository implements HomeRepository {

    private static final String LIST = """
            SELECT player_uuid, market, name, world, x, y, z, yaw, pitch
            FROM player_homes
            WHERE player_uuid = ? AND market = ?
            ORDER BY name
            """;

    private static final String FIND = """
            SELECT player_uuid, market, name, world, x, y, z, yaw, pitch
            FROM player_homes
            WHERE player_uuid = ? AND market = ? AND name = ?
            """;

    private static final String COUNT = """
            SELECT COUNT(*) FROM player_homes
            WHERE player_uuid = ? AND market = ?
            """;

    private static final String UPSERT = """
            INSERT INTO player_homes (player_uuid, market, name, world, x, y, z, yaw, pitch)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (player_uuid, market, name) DO UPDATE SET
                world = EXCLUDED.world,
                x = EXCLUDED.x,
                y = EXCLUDED.y,
                z = EXCLUDED.z,
                yaw = EXCLUDED.yaw,
                pitch = EXCLUDED.pitch,
                updated_at = now()
            """;

    private static final String DELETE = """
            DELETE FROM player_homes
            WHERE player_uuid = ? AND market = ? AND name = ?
            """;

    private final Supplier<DataSource> dataSource;
    private final String market;

    public PostgresHomeRepository(Supplier<DataSource> dataSource, String market) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.market = Objects.requireNonNull(market, "market");
    }

    @Override
    public List<Home> list(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(LIST)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, market);
            try (ResultSet rows = statement.executeQuery()) {
                List<Home> homes = new ArrayList<>();
                while (rows.next()) {
                    homes.add(map(rows));
                }
                return homes;
            }
        } catch (SQLException e) {
            throw new HomePersistenceException("list homes failed for " + playerUuid, e);
        }
    }

    @Override
    public Optional<Home> find(UUID playerUuid, String name) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, market);
            statement.setString(3, name);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rows));
            }
        } catch (SQLException e) {
            throw new HomePersistenceException("find home failed for " + playerUuid, e);
        }
    }

    @Override
    public int count(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, market);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        } catch (SQLException e) {
            throw new HomePersistenceException("count homes failed for " + playerUuid, e);
        }
    }

    @Override
    public void upsert(Home home) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setObject(1, home.playerUuid());
            statement.setString(2, market);
            statement.setString(3, home.name());
            statement.setString(4, home.world());
            statement.setDouble(5, home.x());
            statement.setDouble(6, home.y());
            statement.setDouble(7, home.z());
            statement.setFloat(8, home.yaw());
            statement.setFloat(9, home.pitch());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new HomePersistenceException("upsert home failed for " + home.playerUuid(), e);
        }
    }

    @Override
    public boolean delete(UUID playerUuid, String name) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setObject(1, playerUuid);
            statement.setString(2, market);
            statement.setString(3, name);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new HomePersistenceException("delete home failed for " + playerUuid, e);
        }
    }

    private static Home map(ResultSet rows) throws SQLException {
        return new Home(
                rows.getObject("player_uuid", UUID.class),
                rows.getString("market"),
                rows.getString("name"),
                rows.getString("world"),
                rows.getDouble("x"),
                rows.getDouble("y"),
                rows.getDouble("z"),
                rows.getFloat("yaw"),
                rows.getFloat("pitch"));
    }
}
