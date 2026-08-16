package com.glyph.core.smp.warp;

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

public final class PostgresWarpRepository implements WarpRepository {

    private static final String LIST_ALL = """
            SELECT name, owner_uuid, market, world, x, y, z, yaw, pitch
            FROM player_warps
            WHERE market = ?
            ORDER BY name
            """;

    private static final String LIST_OWNED = """
            SELECT name, owner_uuid, market, world, x, y, z, yaw, pitch
            FROM player_warps
            WHERE market = ? AND owner_uuid = ?
            ORDER BY name
            """;

    private static final String FIND = """
            SELECT name, owner_uuid, market, world, x, y, z, yaw, pitch
            FROM player_warps
            WHERE market = ? AND name = ?
            """;

    private static final String COUNT = """
            SELECT COUNT(*) FROM player_warps
            WHERE market = ? AND owner_uuid = ?
            """;

    private static final String INSERT = """
            INSERT INTO player_warps (name, owner_uuid, market, world, x, y, z, yaw, pitch)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String DELETE = """
            DELETE FROM player_warps
            WHERE market = ? AND name = ? AND owner_uuid = ?
            """;

    private final Supplier<DataSource> dataSource;
    private final String market;

    public PostgresWarpRepository(Supplier<DataSource> dataSource, String market) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.market = Objects.requireNonNull(market, "market");
    }

    @Override
    public List<PlayerWarp> listAll() {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(LIST_ALL)) {
            statement.setString(1, market);
            try (ResultSet rows = statement.executeQuery()) {
                List<PlayerWarp> warps = new ArrayList<>();
                while (rows.next()) {
                    warps.add(map(rows));
                }
                return warps;
            }
        } catch (SQLException e) {
            throw new WarpPersistenceException("list warps failed", e);
        }
    }

    @Override
    public List<PlayerWarp> listOwned(UUID ownerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(LIST_OWNED)) {
            statement.setString(1, market);
            statement.setObject(2, ownerUuid);
            try (ResultSet rows = statement.executeQuery()) {
                List<PlayerWarp> warps = new ArrayList<>();
                while (rows.next()) {
                    warps.add(map(rows));
                }
                return warps;
            }
        } catch (SQLException e) {
            throw new WarpPersistenceException("list owned warps failed for " + ownerUuid, e);
        }
    }

    @Override
    public Optional<PlayerWarp> find(String name) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setString(1, market);
            statement.setString(2, name);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rows));
            }
        } catch (SQLException e) {
            throw new WarpPersistenceException("find warp failed for " + name, e);
        }
    }

    @Override
    public int countOwned(UUID ownerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT)) {
            statement.setString(1, market);
            statement.setObject(2, ownerUuid);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        } catch (SQLException e) {
            throw new WarpPersistenceException("count warps failed for " + ownerUuid, e);
        }
    }

    @Override
    public void insert(PlayerWarp warp) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setString(1, warp.name());
            statement.setObject(2, warp.ownerUuid());
            statement.setString(3, market);
            statement.setString(4, warp.world());
            statement.setDouble(5, warp.x());
            statement.setDouble(6, warp.y());
            statement.setDouble(7, warp.z());
            statement.setFloat(8, warp.yaw());
            statement.setFloat(9, warp.pitch());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new WarpPersistenceException("insert warp failed for " + warp.name(), e);
        }
    }

    @Override
    public boolean delete(String name, UUID ownerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setString(1, market);
            statement.setString(2, name);
            statement.setObject(3, ownerUuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new WarpPersistenceException("delete warp failed for " + name, e);
        }
    }

    private static PlayerWarp map(ResultSet rows) throws SQLException {
        return new PlayerWarp(
                rows.getString("name"),
                rows.getObject("owner_uuid", UUID.class),
                rows.getString("market"),
                rows.getString("world"),
                rows.getDouble("x"),
                rows.getDouble("y"),
                rows.getDouble("z"),
                rows.getFloat("yaw"),
                rows.getFloat("pitch"));
    }
}
