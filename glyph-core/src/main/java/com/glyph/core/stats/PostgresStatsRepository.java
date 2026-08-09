package com.glyph.core.stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** JDBC implementation of {@link StatsRepository}: one batched upsert per flush. */
public final class PostgresStatsRepository implements StatsRepository {

    private static final String UPSERT = """
            INSERT INTO player_stats AS s
                (player_uuid, kills, deaths, mob_kills, blocks_broken, blocks_placed,
                 distance_cm, auction_sales, auction_purchases, bounties_claimed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (player_uuid) DO UPDATE SET
                kills             = s.kills             + EXCLUDED.kills,
                deaths            = s.deaths            + EXCLUDED.deaths,
                mob_kills         = s.mob_kills         + EXCLUDED.mob_kills,
                blocks_broken     = s.blocks_broken     + EXCLUDED.blocks_broken,
                blocks_placed     = s.blocks_placed     + EXCLUDED.blocks_placed,
                distance_cm       = s.distance_cm       + EXCLUDED.distance_cm,
                auction_sales     = s.auction_sales     + EXCLUDED.auction_sales,
                auction_purchases = s.auction_purchases + EXCLUDED.auction_purchases,
                bounties_claimed  = s.bounties_claimed  + EXCLUDED.bounties_claimed,
                updated_at        = now()
            """;

    private static final String SELECT = """
            SELECT player_uuid, kills, deaths, mob_kills, blocks_broken, blocks_placed,
                   distance_cm, auction_sales, auction_purchases, bounties_claimed
            FROM player_stats
            WHERE player_uuid = ?
            """;

    /** Column order must match the UPSERT parameter list. */
    private static final StatType[] COLUMN_ORDER = {
            StatType.KILLS, StatType.DEATHS, StatType.MOB_KILLS,
            StatType.BLOCKS_BROKEN, StatType.BLOCKS_PLACED, StatType.DISTANCE_CM,
            StatType.AUCTION_SALES, StatType.AUCTION_PURCHASES, StatType.BOUNTIES_CLAIMED,
    };

    private final Supplier<DataSource> dataSource;

    public PostgresStatsRepository(Supplier<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void addDeltas(Map<UUID, Map<StatType, Long>> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            for (Map.Entry<UUID, Map<StatType, Long>> entry : deltas.entrySet()) {
                statement.setObject(1, entry.getKey());
                for (int i = 0; i < COLUMN_ORDER.length; i++) {
                    statement.setLong(i + 2, entry.getValue().getOrDefault(COLUMN_ORDER[i], 0L));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new StatsPersistenceException(
                    "stats flush failed for " + deltas.size() + " player(s)", e);
        }
    }

    @Override
    public Optional<PlayerStats> find(UUID playerUuid) {
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setObject(1, playerUuid);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerStats(
                        row.getObject("player_uuid", UUID.class),
                        row.getLong("kills"),
                        row.getLong("deaths"),
                        row.getLong("mob_kills"),
                        row.getLong("blocks_broken"),
                        row.getLong("blocks_placed"),
                        row.getLong("distance_cm"),
                        row.getLong("auction_sales"),
                        row.getLong("auction_purchases"),
                        row.getLong("bounties_claimed")));
            }
        } catch (SQLException e) {
            throw new StatsPersistenceException("stats lookup failed for " + playerUuid, e);
        }
    }
}
