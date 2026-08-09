package com.glyph.core.auction;

import com.glyph.core.auction.AuctionRepository.BrowsePage;
import com.glyph.core.auction.AuctionRepository.BrowseQuery;
import com.glyph.core.auction.AuctionRepository.CancelStatus;
import com.glyph.core.auction.AuctionRepository.CreateResult;
import com.glyph.core.auction.AuctionRepository.CreateStatus;
import com.glyph.core.auction.AuctionRepository.PurchaseResult;
import com.glyph.core.auction.AuctionRepository.PurchaseStatus;
import com.glyph.core.config.AuctionSettings;
import com.glyph.core.item.ItemCodec;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Auction house orchestration (GDD sections 21-22). Validation lives here;
 * atomicity lives in the repository; everything runs on the async executor —
 * callers are responsible for the entity-thread half (taking/giving items).
 */
public final class AuctionService {

    private final AuctionRepository repository;
    private final AuctionSettings settings;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    /** Notified with (buyerUuid, sellerUuid) after each committed sale. */
    private final List<BiConsumer<UUID, UUID>> purchaseListeners = new CopyOnWriteArrayList<>();

    public AuctionService(
            AuctionRepository repository,
            AuctionSettings settings,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public AuctionSettings settings() {
        return settings;
    }

    public void addPurchaseListener(BiConsumer<UUID, UUID> listener) {
        purchaseListeners.add(listener);
    }

    /**
     * Persists a listing for an item the caller has already removed from the
     * seller's inventory (GDD section 22 steps 2-6). On any failure the
     * caller must hand the item back.
     */
    public CompletableFuture<CreateResult> list(
            UUID sellerUuid, String sellerName, ItemStack item, long price) {
        if (price <= 0) {
            return CompletableFuture.completedFuture(
                    CreateResult.failure(CreateStatus.FAILED));
        }
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(
                    CreateResult.failure(CreateStatus.FAILED));
        }
        byte[] itemData = ItemCodec.serialize(item);
        String summary = ItemCodec.summarize(item, sellerName).toJson();
        long listingFee = settings.listingFee(price);
        return CompletableFuture
                .supplyAsync(() -> repository.create(
                        sellerUuid, itemData, summary, price, listingFee,
                        settings.durationHours(), settings.maxListingsPerPlayer()), ioExecutor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Auction listing failed for {}", sellerUuid, error);
                    } else if (result.status() == CreateStatus.SUCCESS) {
                        logger.info("Auction listed: {} by {} for {} (fee {})",
                                result.listing().orElseThrow().id(), sellerName,
                                price, listingFee);
                    }
                })
                .exceptionally(error -> CreateResult.failure(CreateStatus.FAILED));
    }

    public CompletableFuture<PurchaseResult> purchase(UUID listingId, UUID buyerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(
                    PurchaseResult.failure(PurchaseStatus.FAILED));
        }
        return CompletableFuture
                .supplyAsync(() -> {
                    AuctionListing listing = repository.find(listingId).orElse(null);
                    long saleFee = listing == null ? 0 : settings.saleFee(listing.price());
                    return repository.purchase(listingId, buyerUuid, saleFee);
                }, ioExecutor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Auction purchase failed: listing {} buyer {}",
                                listingId, buyerUuid, error);
                    } else if (result.status() == PurchaseStatus.SUCCESS) {
                        AuctionListing sold = result.listing().orElseThrow();
                        logger.info("Auction sold: {} to {} for {}",
                                listingId, buyerUuid, sold.price());
                        for (BiConsumer<UUID, UUID> listener : purchaseListeners) {
                            try {
                                listener.accept(buyerUuid, sold.sellerUuid());
                            } catch (Exception e) {
                                logger.error("Auction purchase listener failed", e);
                            }
                        }
                    }
                })
                .exceptionally(error -> PurchaseResult.failure(PurchaseStatus.FAILED));
    }

    public CompletableFuture<CancelStatus> cancel(UUID listingId, UUID sellerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(CancelStatus.NO_LONGER_ACTIVE);
        }
        return CompletableFuture
                .supplyAsync(() -> repository.cancel(listingId, sellerUuid), ioExecutor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error("Auction cancel failed: listing {}", listingId, error);
                    } else if (result == CancelStatus.SUCCESS) {
                        logger.info("Auction cancelled: {} by {}", listingId, sellerUuid);
                    }
                });
    }

    public CompletableFuture<BrowsePage> browse(BrowseQuery query) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Auction house unavailable: database is down"));
        }
        return CompletableFuture.supplyAsync(() -> repository.browse(query), ioExecutor);
    }

    /** Periodic expiry sweep; returned count is logged when non-zero. */
    public void sweepExpired() {
        if (!databaseReady.getAsBoolean()) {
            return;
        }
        try {
            int expired = repository.expireDue();
            if (expired > 0) {
                logger.info("Auction expiry sweep: {} listing(s) returned to sellers", expired);
            }
        } catch (Exception e) {
            logger.error("Auction expiry sweep failed", e);
        }
    }
}
