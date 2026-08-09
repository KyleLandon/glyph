package com.glyph.core.auction;

import com.glyph.api.economy.TransactionType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;

/**
 * JDBC implementation of {@link AuctionRepository}.
 *
 * <p>Concurrency rules, mirroring {@code PostgresEconomyRepository}:</p>
 * <ul>
 *   <li>The listing row is locked with {@code SELECT ... FOR UPDATE} before
 *       any check — two simultaneous buyers serialize on this lock and the
 *       second sees status SOLD (GDD section 102's critical test).</li>
 *   <li>Buyer and seller account rows are locked in one statement ordered
 *       by account id, so purchases cannot deadlock against transfers.</li>
 *   <li>Deliveries are inserted in the same transaction that moves money;
 *       the item is never claimable unless the payment committed.</li>
 *   <li>Fees are burned (ledger rows with null destination): auction fees
 *       are money sinks (GDD section 17).</li>
 * </ul>
 */
public final class PostgresAuctionRepository implements AuctionRepository {

    private static final String INSERT_LISTING = """
            INSERT INTO auction_listings
                (id, seller_uuid, seller_account, item_data, item_summary, price,
                 listing_fee, status, expires_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, 'ACTIVE', ?)
            """;

    private static final String LOCK_LISTING = """
            SELECT id, seller_uuid, seller_account, item_data, item_summary, price,
                   listing_fee, status, buyer_uuid, created_at, expires_at, sold_at
            FROM auction_listings
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String LOCK_ACCOUNT_BY_OWNER = """
            SELECT id, owner_uuid, balance FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid = ?
            FOR UPDATE
            """;

    private static final String LOCK_TWO_ACCOUNTS = """
            SELECT id, owner_uuid, balance FROM accounts
            WHERE owner_type = 'PLAYER' AND owner_uuid IN (?, ?)
            ORDER BY id
            FOR UPDATE
            """;

    private static final String DEBIT = """
            UPDATE accounts SET
                balance        = balance - ?,
                lifetime_spent = lifetime_spent + ?,
                updated_at     = now()
            WHERE id = ?
            """;

    private static final String CREDIT = """
            UPDATE accounts SET
                balance         = balance + ?,
                lifetime_earned = lifetime_earned + ?,
                updated_at      = now()
            WHERE id = ?
            """;

    private static final String INSERT_LEDGER = """
            INSERT INTO transactions
                (id, source_account, destination_account, amount, type, reason,
                 related_entity, actor_uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String MARK_SOLD = """
            UPDATE auction_listings
            SET status = 'SOLD', buyer_uuid = ?, sold_at = now()
            WHERE id = ?
            """;

    private static final String MARK_CANCELLED =
            "UPDATE auction_listings SET status = 'CANCELLED' WHERE id = ?";

    private static final String EXPIRE_DUE = """
            UPDATE auction_listings
            SET status = 'EXPIRED'
            WHERE status = 'ACTIVE' AND expires_at <= now()
            RETURNING id, seller_uuid, item_data, item_summary, price
            """;

    private static final String INSERT_DELIVERY = """
            INSERT INTO deliveries (id, recipient_uuid, type, payload, metadata, status)
            VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING')
            """;

    private static final String COUNT_ACTIVE_BY_SELLER =
            "SELECT count(*) FROM auction_listings WHERE seller_uuid = ? AND status = 'ACTIVE'";

    private final Supplier<DataSource> dataSource;

    public PostgresAuctionRepository(Supplier<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    private record LockedAccount(UUID accountId, long balance) { }

    @Override
    public CreateResult create(UUID sellerUuid, byte[] itemData, String summaryJson,
                               long price, long listingFee,
                               int durationHours, int maxActivePerSeller) {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                LockedAccount seller = lockAccount(connection, sellerUuid);
                if (seller == null) {
                    connection.rollback();
                    return CreateResult.failure(CreateStatus.ACCOUNT_NOT_FOUND);
                }
                if (seller.balance() < listingFee) {
                    connection.rollback();
                    return CreateResult.failure(CreateStatus.INSUFFICIENT_FUNDS);
                }
                // The seller's account lock serializes concurrent listings by
                // the same player, making this count check race-free.
                if (countActive(connection, sellerUuid) >= maxActivePerSeller) {
                    connection.rollback();
                    return CreateResult.failure(CreateStatus.LIMIT_REACHED);
                }

                UUID listingId = UUID.randomUUID();
                Instant expiresAt = Instant.now().plus(durationHours, ChronoUnit.HOURS);
                try (PreparedStatement statement = connection.prepareStatement(INSERT_LISTING)) {
                    statement.setObject(1, listingId);
                    statement.setObject(2, sellerUuid);
                    statement.setObject(3, seller.accountId());
                    statement.setBytes(4, itemData);
                    statement.setString(5, summaryJson);
                    statement.setLong(6, price);
                    statement.setLong(7, listingFee);
                    statement.setTimestamp(8, Timestamp.from(expiresAt));
                    statement.executeUpdate();
                }

                if (listingFee > 0) {
                    executeMoney(connection, DEBIT, listingFee, seller.accountId());
                    ledger(connection, seller.accountId(), null, listingFee,
                            TransactionType.AUCTION_FEE, "auction listing fee",
                            listingId, sellerUuid);
                }

                connection.commit();
                AuctionListing listing = new AuctionListing(
                        listingId, sellerUuid, itemData, summaryJson, price,
                        listingFee, AuctionListing.Status.ACTIVE, Optional.empty(),
                        Instant.now(), expiresAt, Optional.empty());
                return new CreateResult(CreateStatus.SUCCESS, Optional.of(listing));
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new AuctionPersistenceException("listing creation failed for " + sellerUuid, e);
        }
    }

    @Override
    public PurchaseResult purchase(UUID listingId, UUID buyerUuid, long saleFee) {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                AuctionListing listing = lockListing(connection, listingId);
                if (listing == null) {
                    connection.rollback();
                    return PurchaseResult.failure(PurchaseStatus.NOT_FOUND);
                }
                // Expired-but-unswept listings must not be purchasable.
                if (listing.status() != AuctionListing.Status.ACTIVE
                        || !listing.expiresAt().isAfter(Instant.now())) {
                    connection.rollback();
                    return PurchaseResult.failure(PurchaseStatus.NO_LONGER_ACTIVE);
                }
                if (listing.sellerUuid().equals(buyerUuid)) {
                    connection.rollback();
                    return PurchaseResult.failure(PurchaseStatus.SELF_PURCHASE);
                }

                LockedAccount buyer = null;
                LockedAccount seller = null;
                try (PreparedStatement statement =
                             connection.prepareStatement(LOCK_TWO_ACCOUNTS)) {
                    statement.setObject(1, buyerUuid);
                    statement.setObject(2, listing.sellerUuid());
                    try (ResultSet row = statement.executeQuery()) {
                        while (row.next()) {
                            LockedAccount account = new LockedAccount(
                                    row.getObject("id", UUID.class), row.getLong("balance"));
                            UUID owner = row.getObject("owner_uuid", UUID.class);
                            if (owner.equals(buyerUuid)) {
                                buyer = account;
                            }
                            if (owner.equals(listing.sellerUuid())) {
                                seller = account;
                            }
                        }
                    }
                }
                if (buyer == null || seller == null) {
                    connection.rollback();
                    return PurchaseResult.failure(PurchaseStatus.ACCOUNT_NOT_FOUND);
                }

                long price = listing.price();
                if (buyer.balance() < price) {
                    connection.rollback();
                    return PurchaseResult.failure(PurchaseStatus.INSUFFICIENT_FUNDS);
                }
                long fee = Math.min(saleFee, price);
                long sellerProceeds = price - fee;

                executeMoney(connection, DEBIT, price, buyer.accountId());
                if (sellerProceeds > 0) {
                    executeMoney(connection, CREDIT, sellerProceeds, seller.accountId());
                }

                try (PreparedStatement statement = connection.prepareStatement(MARK_SOLD)) {
                    statement.setObject(1, buyerUuid);
                    statement.setObject(2, listingId);
                    statement.executeUpdate();
                }

                insertDelivery(connection, buyerUuid, "AUCTION_ITEM", listing.itemData(),
                        deliveryMetadata("AUCTION_SOLD", listingId, price));

                ledger(connection, buyer.accountId(), seller.accountId(), price,
                        TransactionType.AUCTION_PURCHASE, "auction purchase",
                        listingId, buyerUuid);
                if (fee > 0) {
                    ledger(connection, seller.accountId(), null, fee,
                            TransactionType.AUCTION_FEE, "auction sale fee",
                            listingId, buyerUuid);
                }

                connection.commit();
                AuctionListing sold = new AuctionListing(
                        listing.id(), listing.sellerUuid(), listing.itemData(),
                        listing.summaryJson(), listing.price(), listing.listingFee(),
                        AuctionListing.Status.SOLD, Optional.of(buyerUuid),
                        listing.createdAt(), listing.expiresAt(), Optional.of(Instant.now()));
                return new PurchaseResult(
                        PurchaseStatus.SUCCESS, buyer.balance() - price, Optional.of(sold));
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new AuctionPersistenceException(
                    "purchase failed: listing " + listingId + " buyer " + buyerUuid, e);
        }
    }

    @Override
    public CancelStatus cancel(UUID listingId, UUID sellerUuid) {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                AuctionListing listing = lockListing(connection, listingId);
                if (listing == null) {
                    connection.rollback();
                    return CancelStatus.NOT_FOUND;
                }
                if (!listing.sellerUuid().equals(sellerUuid)) {
                    connection.rollback();
                    return CancelStatus.NOT_OWNER;
                }
                if (listing.status() != AuctionListing.Status.ACTIVE) {
                    connection.rollback();
                    return CancelStatus.NO_LONGER_ACTIVE;
                }

                try (PreparedStatement statement = connection.prepareStatement(MARK_CANCELLED)) {
                    statement.setObject(1, listingId);
                    statement.executeUpdate();
                }
                insertDelivery(connection, sellerUuid, "AUCTION_RETURN", listing.itemData(),
                        deliveryMetadata("AUCTION_CANCELLED", listingId, listing.price()));

                connection.commit();
                return CancelStatus.SUCCESS;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new AuctionPersistenceException("cancel failed: listing " + listingId, e);
        }
    }

    @Override
    public int expireDue() {
        try (Connection connection = dataSource.get().getConnection()) {
            connection.setAutoCommit(false);
            try {
                record Expired(UUID id, UUID seller, byte[] itemData, long price) { }
                List<Expired> expired = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(EXPIRE_DUE);
                     ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        expired.add(new Expired(
                                row.getObject("id", UUID.class),
                                row.getObject("seller_uuid", UUID.class),
                                row.getBytes("item_data"),
                                row.getLong("price")));
                    }
                }
                for (Expired entry : expired) {
                    insertDelivery(connection, entry.seller(), "AUCTION_RETURN", entry.itemData(),
                            deliveryMetadata("AUCTION_EXPIRED", entry.id(), entry.price()));
                }
                connection.commit();
                return expired.size();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new AuctionPersistenceException("expiry sweep failed", e);
        }
    }

    @Override
    public BrowsePage browse(BrowseQuery query) {
        StringBuilder where = new StringBuilder(
                "WHERE status = 'ACTIVE' AND expires_at > now()");
        List<Object> parameters = new ArrayList<>();
        if (query.sellerFilter() != null) {
            where.append(" AND seller_uuid = ?");
            parameters.add(query.sellerFilter());
        }
        if (query.category() != null) {
            where.append(" AND item_summary->>'category' = ?");
            parameters.add(query.category());
        }
        if (query.search() != null && !query.search().isBlank()) {
            where.append(" AND (item_summary->>'material' ILIKE ?"
                    + " OR item_summary->>'name' ILIKE ?)");
            String like = "%" + query.search().trim() + "%";
            parameters.add(like);
            parameters.add(like);
        }
        String order = switch (query.sort()) {
            case NEWEST -> "created_at DESC";
            case PRICE_ASC -> "price ASC, created_at DESC";
            case PRICE_DESC -> "price DESC, created_at DESC";
        };

        String listSql = "SELECT id, seller_uuid, seller_account, item_data, item_summary,"
                + " price, listing_fee, status, buyer_uuid, created_at, expires_at, sold_at"
                + " FROM auction_listings " + where + " ORDER BY " + order + " LIMIT ? OFFSET ?";
        String countSql = "SELECT count(*) FROM auction_listings " + where;

        try (Connection connection = dataSource.get().getConnection()) {
            int total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bind(statement, parameters);
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    total = row.getInt(1);
                }
            }
            List<AuctionListing> listings = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(listSql)) {
                bind(statement, parameters);
                statement.setInt(parameters.size() + 1, query.pageSize());
                statement.setInt(parameters.size() + 2, query.page() * query.pageSize());
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        listings.add(readListing(row));
                    }
                }
            }
            return new BrowsePage(listings, total, query.page(), query.pageSize());
        } catch (SQLException e) {
            throw new AuctionPersistenceException("browse failed", e);
        }
    }

    @Override
    public Optional<AuctionListing> find(UUID listingId) {
        String sql = "SELECT id, seller_uuid, seller_account, item_data, item_summary, price,"
                + " listing_fee, status, buyer_uuid, created_at, expires_at, sold_at"
                + " FROM auction_listings WHERE id = ?";
        try (Connection connection = dataSource.get().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, listingId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(readListing(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new AuctionPersistenceException("find failed: " + listingId, e);
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters)
            throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private static AuctionListing readListing(ResultSet row) throws SQLException {
        Timestamp soldAt = row.getTimestamp("sold_at");
        return new AuctionListing(
                row.getObject("id", UUID.class),
                row.getObject("seller_uuid", UUID.class),
                row.getBytes("item_data"),
                row.getString("item_summary"),
                row.getLong("price"),
                row.getLong("listing_fee"),
                AuctionListing.Status.valueOf(row.getString("status")),
                Optional.ofNullable(row.getObject("buyer_uuid", UUID.class)),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("expires_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(soldAt).map(Timestamp::toInstant));
    }

    private AuctionListing lockListing(Connection connection, UUID listingId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_LISTING)) {
            statement.setObject(1, listingId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? readListing(row) : null;
            }
        }
    }

    private LockedAccount lockAccount(Connection connection, UUID ownerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ACCOUNT_BY_OWNER)) {
            statement.setObject(1, ownerUuid);
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? new LockedAccount(row.getObject("id", UUID.class), row.getLong("balance"))
                        : null;
            }
        }
    }

    private int countActive(Connection connection, UUID sellerUuid) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(COUNT_ACTIVE_BY_SELLER)) {
            statement.setObject(1, sellerUuid);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    private static void executeMoney(Connection connection, String sql, long amount, UUID accountId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setLong(2, amount);
            statement.setObject(3, accountId);
            statement.executeUpdate();
        }
    }

    private static void ledger(Connection connection, UUID sourceAccount, UUID destAccount,
                               long amount, TransactionType type, String reason,
                               UUID relatedEntity, UUID actor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LEDGER)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, sourceAccount);
            statement.setObject(3, destAccount);
            statement.setLong(4, amount);
            statement.setString(5, type.name());
            statement.setString(6, reason);
            statement.setObject(7, relatedEntity);
            statement.setObject(8, actor);
            statement.executeUpdate();
        }
    }

    private static void insertDelivery(Connection connection, UUID recipient, String type,
                                       byte[] payload, String metadata) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DELIVERY)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, recipient);
            statement.setString(3, type);
            statement.setBytes(4, payload);
            statement.setString(5, metadata);
            statement.executeUpdate();
        }
    }

    private static String deliveryMetadata(String source, UUID listingId, long price) {
        return "{\"source\":\"" + source + "\",\"listing\":\"" + listingId
                + "\",\"price\":" + price + "}";
    }
}
