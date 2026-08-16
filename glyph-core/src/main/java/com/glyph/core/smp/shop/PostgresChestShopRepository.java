package com.glyph.core.smp.shop;

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

public final class PostgresChestShopRepository implements ChestShopRepository {

    private static final String LIST_ALL = """
            SELECT id, owner_uuid, market, world, x, y, z, mode, price, item_data
            FROM chest_shops
            WHERE market = ?
            """;

    private static final String FIND = """
            SELECT id, owner_uuid, market, world, x, y, z, mode, price, item_data
            FROM chest_shops
            WHERE market = ? AND world = ? AND x = ? AND y = ? AND z = ?
            """;

    private static final String INSERT = """
            INSERT INTO chest_shops (id, owner_uuid, market, world, x, y, z, mode, price, item_data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String DELETE = """
            DELETE FROM chest_shops
            WHERE id = ? AND owner_uuid = ?
            """;

    private final Supplier<DataSource> dataSource;
    private final String market;

    public PostgresChestShopRepository(Supplier<DataSource> dataSource, String market) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.market = Objects.requireNonNull(market, "market");
    }

    @Override
    public Optional<ChestShop> findAt(String world, int x, int y, int z) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND)) {
            statement.setString(1, market);
            statement.setString(2, world);
            statement.setInt(3, x);
            statement.setInt(4, y);
            statement.setInt(5, z);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rows));
            }
        } catch (SQLException e) {
            throw new ChestShopPersistenceException("find shop failed", e);
        }
    }

    @Override
    public List<ChestShop> listAll() {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(LIST_ALL)) {
            statement.setString(1, market);
            try (ResultSet rows = statement.executeQuery()) {
                List<ChestShop> shops = new ArrayList<>();
                while (rows.next()) {
                    shops.add(map(rows));
                }
                return shops;
            }
        } catch (SQLException e) {
            throw new ChestShopPersistenceException("list shops failed", e);
        }
    }

    @Override
    public void insert(ChestShop shop) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, shop.id());
            statement.setObject(2, shop.ownerUuid());
            statement.setString(3, market);
            statement.setString(4, shop.world());
            statement.setInt(5, shop.x());
            statement.setInt(6, shop.y());
            statement.setInt(7, shop.z());
            statement.setString(8, shop.mode().name());
            statement.setLong(9, shop.price());
            statement.setBytes(10, shop.itemData());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new ChestShopPersistenceException("insert shop failed", e);
        }
    }

    @Override
    public boolean delete(UUID id, UUID ownerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setObject(1, id);
            statement.setObject(2, ownerUuid);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new ChestShopPersistenceException("delete shop failed", e);
        }
    }

    private static ChestShop map(ResultSet rows) throws SQLException {
        return new ChestShop(
                rows.getObject("id", UUID.class),
                rows.getObject("owner_uuid", UUID.class),
                rows.getString("market"),
                rows.getString("world"),
                rows.getInt("x"),
                rows.getInt("y"),
                rows.getInt("z"),
                ChestShop.Mode.valueOf(rows.getString("mode")),
                rows.getLong("price"),
                rows.getBytes("item_data"));
    }
}
