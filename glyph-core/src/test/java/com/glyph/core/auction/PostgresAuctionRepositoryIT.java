package com.glyph.core.auction;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.core.auction.AuctionRepository.BrowsePage;
import com.glyph.core.auction.AuctionRepository.BrowseQuery;
import com.glyph.core.auction.AuctionRepository.CancelStatus;
import com.glyph.core.auction.AuctionRepository.CreateResult;
import com.glyph.core.auction.AuctionRepository.CreateStatus;
import com.glyph.core.auction.AuctionRepository.PurchaseResult;
import com.glyph.core.auction.AuctionRepository.PurchaseStatus;
import com.glyph.core.auction.AuctionRepository.Sort;
import com.glyph.core.config.DatabaseSettings;
import com.glyph.core.database.DatabaseManager;
import com.glyph.core.delivery.Delivery;
import com.glyph.core.delivery.PostgresDeliveryRepository;
import com.glyph.core.economy.PostgresEconomyRepository;
import com.glyph.core.player.PostgresPlayerRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Auction house integration tests against real PostgreSQL, including the
 * GDD section 102 critical concurrency test: two buyers, one listing,
 * exactly one success. Skipped when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresAuctionRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("glyph_test")
                    .withUsername("glyph_test")
                    .withPassword("glyph_test");

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final byte[] ITEM = {1, 2, 3, 4};

    private static DatabaseManager manager;
    private static PostgresPlayerRepository players;
    private static PostgresEconomyRepository economy;
    private static PostgresAuctionRepository auctions;
    private static PostgresDeliveryRepository deliveries;

    @BeforeAll
    static void initDatabase() {
        manager = new DatabaseManager(
                new DatabaseSettings(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword(),
                        2, 8, 5_000, 60_000, 300_000),
                LoggerFactory.getLogger("test"), EXECUTOR);
        manager.initAsync().join();
        players = new PostgresPlayerRepository(manager::dataSource, 0);
        economy = new PostgresEconomyRepository(manager::dataSource);
        auctions = new PostgresAuctionRepository(manager::dataSource);
        deliveries = new PostgresDeliveryRepository(manager::dataSource);
    }

    @AfterAll
    static void shutdown() {
        if (manager != null) {
            manager.close();
        }
        EXECUTOR.shutdownNow();
    }

    private static UUID playerWithBalance(long balance) {
        UUID uuid = UUID.randomUUID();
        players.recordJoin(uuid, "P" + uuid.toString().substring(0, 8));
        if (balance > 0) {
            economy.adminAdjust(uuid, AdminOperation.SET, balance, null);
        }
        return uuid;
    }

    private static String summary(String material, String category, String seller) {
        return "{\"material\":\"" + material + "\",\"amount\":1,\"name\":null,"
                + "\"category\":\"" + category + "\",\"seller\":\"" + seller + "\"}";
    }

    private static AuctionListing listing(UUID seller, long price) {
        CreateResult result = auctions.create(seller, ITEM,
                summary("DIAMOND_SWORD", "WEAPONS", "Seller"), price, 0, 48, 100);
        assertThat(result.status()).isEqualTo(CreateStatus.SUCCESS);
        return result.listing().orElseThrow();
    }

    @Test
    void listingChargesFeeAndAppearsInBrowse() {
        UUID seller = playerWithBalance(10_00);

        CreateResult result = auctions.create(seller, ITEM,
                summary("DIAMOND_SWORD", "WEAPONS", "Seller"), 500_00, 5_00, 48, 10);

        assertThat(result.status()).isEqualTo(CreateStatus.SUCCESS);
        assertThat(economy.balance(seller)).contains(5_00L);

        BrowsePage page = auctions.browse(new BrowseQuery(
                0, 45, Sort.NEWEST, null, null, seller));
        assertThat(page.listings()).hasSize(1);
        assertThat(page.listings().getFirst().price()).isEqualTo(500_00);
    }

    @Test
    void listingFailsWithoutFeeFunds() {
        UUID seller = playerWithBalance(1_00);

        CreateResult result = auctions.create(seller, ITEM,
                summary("DIRT", "BLOCKS", "Seller"), 500_00, 5_00, 48, 10);

        assertThat(result.status()).isEqualTo(CreateStatus.INSUFFICIENT_FUNDS);
        assertThat(economy.balance(seller)).contains(1_00L);
    }

    @Test
    void listingLimitEnforced() {
        UUID seller = playerWithBalance(0);
        auctions.create(seller, ITEM, summary("DIRT", "BLOCKS", "S"), 1_00, 0, 48, 2);
        auctions.create(seller, ITEM, summary("DIRT", "BLOCKS", "S"), 1_00, 0, 48, 2);

        CreateResult third = auctions.create(seller, ITEM,
                summary("DIRT", "BLOCKS", "S"), 1_00, 0, 48, 2);

        assertThat(third.status()).isEqualTo(CreateStatus.LIMIT_REACHED);
    }

    @Test
    void purchaseMovesMoneyMarksSoldAndQueuesDelivery() {
        UUID seller = playerWithBalance(0);
        UUID buyer = playerWithBalance(100_00);
        AuctionListing listing = listing(seller, 60_00);

        // 5% sale fee = 3_00; seller receives 57_00.
        PurchaseResult result = auctions.purchase(listing.id(), buyer, 3_00);

        assertThat(result.status()).isEqualTo(PurchaseStatus.SUCCESS);
        assertThat(result.buyerBalanceAfter()).isEqualTo(40_00);
        assertThat(economy.balance(buyer)).contains(40_00L);
        assertThat(economy.balance(seller)).contains(57_00L);

        AuctionListing sold = auctions.find(listing.id()).orElseThrow();
        assertThat(sold.status()).isEqualTo(AuctionListing.Status.SOLD);
        assertThat(sold.buyerUuid()).contains(buyer);
        assertThat(sold.soldAt()).isPresent();

        List<Delivery> claimed = deliveries.claim(buyer, 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().type()).isEqualTo("AUCTION_ITEM");
        assertThat(claimed.getFirst().payload()).isEqualTo(ITEM);
    }

    @Test
    void purchaseFailsWithoutFunds() {
        UUID seller = playerWithBalance(0);
        UUID buyer = playerWithBalance(1_00);
        AuctionListing listing = listing(seller, 60_00);

        PurchaseResult result = auctions.purchase(listing.id(), buyer, 0);

        assertThat(result.status()).isEqualTo(PurchaseStatus.INSUFFICIENT_FUNDS);
        assertThat(economy.balance(buyer)).contains(1_00L);
        assertThat(auctions.find(listing.id()).orElseThrow().status())
                .isEqualTo(AuctionListing.Status.ACTIVE);
        assertThat(deliveries.pendingCount(buyer)).isZero();
    }

    @Test
    void ownListingCannotBeBought() {
        UUID seller = playerWithBalance(100_00);
        AuctionListing listing = listing(seller, 10_00);

        assertThat(auctions.purchase(listing.id(), seller, 0).status())
                .isEqualTo(PurchaseStatus.SELF_PURCHASE);
    }

    /**
     * GDD section 102: two buyers attempt to purchase the same auction
     * simultaneously — exactly one succeeds, money is conserved, and only
     * one delivery exists.
     */
    @Test
    void concurrentPurchaseSellsExactlyOnce() throws Exception {
        UUID seller = playerWithBalance(0);
        AuctionListing listing = listing(seller, 50_00);
        int buyers = 8;

        List<UUID> buyerIds = new java.util.ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            buyerIds.add(playerWithBalance(100_00));
        }

        List<Future<PurchaseResult>> futures = new java.util.ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(buyers)) {
            CountDownLatch start = new CountDownLatch(1);
            for (UUID buyer : buyerIds) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return auctions.purchase(listing.id(), buyer, 0);
                }));
            }
            start.countDown();
        }

        long successes = 0;
        for (Future<PurchaseResult> future : futures) {
            PurchaseResult result = future.get();
            if (result.status() == PurchaseStatus.SUCCESS) {
                successes++;
            } else {
                assertThat(result.status()).isEqualTo(PurchaseStatus.NO_LONGER_ACTIVE);
            }
        }
        assertThat(successes).isEqualTo(1);

        // Exactly one buyer paid; everyone else is untouched.
        long paidBuyers = buyerIds.stream()
                .filter(buyer -> economy.balance(buyer).orElseThrow() == 50_00L)
                .count();
        assertThat(paidBuyers).isEqualTo(1);
        assertThat(economy.balance(seller)).contains(50_00L);

        // Exactly one AUCTION_ITEM delivery for this listing.
        try (Connection connection = manager.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM deliveries WHERE metadata->>'listing' = ?")) {
            statement.setString(1, listing.id().toString());
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                assertThat(row.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void cancelReturnsItemToSeller() {
        UUID seller = playerWithBalance(0);
        AuctionListing listing = listing(seller, 10_00);

        assertThat(auctions.cancel(listing.id(), seller)).isEqualTo(CancelStatus.SUCCESS);
        assertThat(auctions.find(listing.id()).orElseThrow().status())
                .isEqualTo(AuctionListing.Status.CANCELLED);

        List<Delivery> claimed = deliveries.claim(seller, 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().type()).isEqualTo("AUCTION_RETURN");

        // A cancelled listing can neither be bought nor cancelled again.
        UUID buyer = playerWithBalance(100_00);
        assertThat(auctions.purchase(listing.id(), buyer, 0).status())
                .isEqualTo(PurchaseStatus.NO_LONGER_ACTIVE);
        assertThat(auctions.cancel(listing.id(), seller))
                .isEqualTo(CancelStatus.NO_LONGER_ACTIVE);
    }

    @Test
    void onlyTheSellerCanCancel() {
        UUID seller = playerWithBalance(0);
        UUID stranger = playerWithBalance(0);
        AuctionListing listing = listing(seller, 10_00);

        assertThat(auctions.cancel(listing.id(), stranger)).isEqualTo(CancelStatus.NOT_OWNER);
        assertThat(auctions.find(listing.id()).orElseThrow().status())
                .isEqualTo(AuctionListing.Status.ACTIVE);
    }

    @Test
    void expirySweepReturnsItemsAndBlocksPurchase() throws Exception {
        UUID seller = playerWithBalance(0);
        AuctionListing listing = listing(seller, 10_00);

        // Force the listing overdue, then sweep.
        try (Connection connection = manager.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE auction_listings SET expires_at = now() - interval '1 minute'"
                             + " WHERE id = ?")) {
            statement.setObject(1, listing.id());
            statement.executeUpdate();
        }

        // Overdue but not yet swept: purchase must already be impossible.
        UUID buyer = playerWithBalance(100_00);
        assertThat(auctions.purchase(listing.id(), buyer, 0).status())
                .isEqualTo(PurchaseStatus.NO_LONGER_ACTIVE);

        assertThat(auctions.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(auctions.find(listing.id()).orElseThrow().status())
                .isEqualTo(AuctionListing.Status.EXPIRED);
        assertThat(deliveries.claim(seller, 10)).hasSize(1);
    }

    @Test
    void browseFiltersSortsAndPaginates() {
        UUID seller = playerWithBalance(0);
        auctions.create(seller, ITEM, summary("DIAMOND_SWORD", "WEAPONS", "S"), 30_00, 0, 48, 100);
        auctions.create(seller, ITEM, summary("IRON_PICKAXE", "TOOLS", "S"), 10_00, 0, 48, 100);
        auctions.create(seller, ITEM, summary("GOLDEN_APPLE", "FOOD", "S"), 20_00, 0, 48, 100);

        BrowsePage weapons = auctions.browse(new BrowseQuery(
                0, 45, Sort.NEWEST, "WEAPONS", null, seller));
        assertThat(weapons.listings()).hasSize(1);

        BrowsePage search = auctions.browse(new BrowseQuery(
                0, 45, Sort.NEWEST, null, "pick", seller));
        assertThat(search.listings()).hasSize(1);

        BrowsePage cheapFirst = auctions.browse(new BrowseQuery(
                0, 45, Sort.PRICE_ASC, null, null, seller));
        assertThat(cheapFirst.listings().getFirst().price()).isEqualTo(10_00);

        BrowsePage paged = auctions.browse(new BrowseQuery(0, 2, Sort.NEWEST, null, null, seller));
        assertThat(paged.listings()).hasSize(2);
        assertThat(paged.totalCount()).isEqualTo(3);
        assertThat(paged.pageCount()).isEqualTo(2);
        BrowsePage lastPage = auctions.browse(new BrowseQuery(
                1, 2, Sort.NEWEST, null, null, seller));
        assertThat(lastPage.listings()).hasSize(1);
    }

    @Test
    void concurrentClaimsNeverHandOutTheSameDeliveryTwice() throws Exception {
        UUID recipient = playerWithBalance(0);
        for (int i = 0; i < 5; i++) {
            deliveries.create(recipient, "ITEM_RETURN", ITEM, "{\"source\":\"TEST\"}");
        }

        List<Future<List<Delivery>>> futures = new java.util.ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return deliveries.claim(recipient, 5);
                }));
            }
            start.countDown();
        }

        long totalClaimed = 0;
        for (Future<List<Delivery>> future : futures) {
            totalClaimed += future.get().size();
        }
        assertThat(totalClaimed).isEqualTo(5);
        assertThat(deliveries.pendingCount(recipient)).isZero();
    }

    @Test
    void revertedClaimsBecomeClaimableAgain() {
        UUID recipient = playerWithBalance(0);
        deliveries.create(recipient, "ITEM_RETURN", ITEM, "{\"source\":\"TEST\"}");

        List<Delivery> claimed = deliveries.claim(recipient, 5);
        assertThat(claimed).hasSize(1);
        assertThat(deliveries.pendingCount(recipient)).isZero();

        deliveries.revert(List.of(claimed.getFirst().id()));
        assertThat(deliveries.pendingCount(recipient)).isEqualTo(1);
        assertThat(deliveries.claim(recipient, 5)).hasSize(1);
    }

    @Test
    void marketsNeverShareListingsOrDeliveries() {
        PostgresAuctionRepository smpAh = new PostgresAuctionRepository(manager::dataSource, "smp");
        PostgresDeliveryRepository smpMail = new PostgresDeliveryRepository(manager::dataSource, "smp");

        UUID seller = playerWithBalance(0);
        UUID buyer = playerWithBalance(100_00);
        AuctionListing anarchyListing = listing(seller, 10_00);
        CreateResult smpListed = smpAh.create(seller, ITEM,
                summary("DIRT", "BLOCKS", "Seller"), 20_00, 0, 48, 100);
        assertThat(smpListed.status()).isEqualTo(CreateStatus.SUCCESS);
        AuctionListing smpListing = smpListed.listing().orElseThrow();

        BrowseQuery mine = new BrowseQuery(0, 45, Sort.NEWEST, null, null, seller);
        assertThat(auctions.browse(mine).listings())
                .extracting(AuctionListing::id)
                .contains(anarchyListing.id())
                .doesNotContain(smpListing.id());
        assertThat(smpAh.browse(mine).listings())
                .extracting(AuctionListing::id)
                .contains(smpListing.id())
                .doesNotContain(anarchyListing.id());

        assertThat(auctions.purchase(smpListing.id(), buyer, 0).status())
                .isEqualTo(PurchaseStatus.NOT_FOUND);
        assertThat(smpAh.purchase(anarchyListing.id(), buyer, 0).status())
                .isEqualTo(PurchaseStatus.NOT_FOUND);

        assertThat(smpAh.purchase(smpListing.id(), buyer, 0).status())
                .isEqualTo(PurchaseStatus.SUCCESS);
        assertThat(deliveries.pendingCount(buyer)).isZero();
        assertThat(smpMail.pendingCount(buyer)).isEqualTo(1);
        assertThat(deliveries.claim(buyer, 10)).isEmpty();
        assertThat(smpMail.claim(buyer, 10)).hasSize(1);
    }
}
