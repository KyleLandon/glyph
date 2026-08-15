package com.glyph.proxy.access;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

public final class PlayerAccessRepository {

    private final Supplier<DataSource> dataSource;

    public PlayerAccessRepository(Supplier<DataSource> dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public boolean hasAlphaAccess(UUID minecraftUuid) throws SQLException {
        String sql = "SELECT alpha FROM player_access WHERE minecraft_uuid = ?";
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
