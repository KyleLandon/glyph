package com.glyph.core.delivery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** JDBC implementation of {@link DeliveryRepository}. */
public final class PostgresDeliveryRepository implements DeliveryRepository {

    private static final String CLAIM = """
            UPDATE deliveries
            SET status = 'CLAIMED', claimed_at = now()
            WHERE id IN (
                SELECT id FROM deliveries
                WHERE recipient_uuid = ? AND status = 'PENDING'
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id, recipient_uuid, type, payload, metadata, created_at
            """;

    private static final String REVERT = """
            UPDATE deliveries
            SET status = 'PENDING', claimed_at = NULL
            WHERE id = ? AND status = 'CLAIMED'
            """;

    private static final String PENDING_COUNT =
            "SELECT count(*) FROM deliveries WHERE recipient_uuid = ? AND status = 'PENDING'";

    private static final String INSERT = """
            INSERT INTO deliveries (id, recipient_uuid, type, payload, metadata, status)
            VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING')
            """;

    private final Supplier<DataSource> dataSource;

    public PostgresDeliveryRepository(Supplier<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void create(UUID recipientUuid, String type, byte[] payload, String metadataJson) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, recipientUuid);
            statement.setString(3, type);
            statement.setBytes(4, payload);
            statement.setString(5, metadataJson);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DeliveryPersistenceException("create failed for " + recipientUuid, e);
        }
    }

    @Override
    public List<Delivery> claim(UUID recipientUuid, int limit) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(CLAIM)) {
            statement.setObject(1, recipientUuid);
            statement.setInt(2, limit);
            try (ResultSet row = statement.executeQuery()) {
                List<Delivery> claimed = new ArrayList<>();
                while (row.next()) {
                    claimed.add(new Delivery(
                            row.getObject("id", UUID.class),
                            row.getObject("recipient_uuid", UUID.class),
                            row.getString("type"),
                            row.getBytes("payload"),
                            row.getString("metadata"),
                            row.getObject("created_at", OffsetDateTime.class).toInstant()));
                }
                return claimed;
            }
        } catch (SQLException e) {
            throw new DeliveryPersistenceException("claim failed for " + recipientUuid, e);
        }
    }

    @Override
    public void revert(List<UUID> deliveryIds) {
        if (deliveryIds.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(REVERT)) {
            for (UUID id : deliveryIds) {
                statement.setObject(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new DeliveryPersistenceException("revert failed", e);
        }
    }

    @Override
    public int pendingCount(UUID recipientUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(PENDING_COUNT)) {
            statement.setObject(1, recipientUuid);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        } catch (SQLException e) {
            throw new DeliveryPersistenceException("pendingCount failed for " + recipientUuid, e);
        }
    }
}
