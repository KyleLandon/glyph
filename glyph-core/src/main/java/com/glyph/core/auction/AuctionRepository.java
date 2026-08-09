package com.glyph.core.auction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for the auction house (GDD sections 21-22).
 *
 * <p>Every mutation is one explicit PostgreSQL transaction. The purchase
 * path implements the GDD section 22 sequence: lock listing, verify ACTIVE,
 * verify funds, move money, mark SOLD, create delivery, ledger, commit —
 * the item is never handed out before the commit succeeds.</p>
 */
public interface AuctionRepository {

    enum Sort { NEWEST, PRICE_ASC, PRICE_DESC }

    /**
     * @param page         zero-based page index
     * @param pageSize     listings per page
     * @param category     {@link com.glyph.core.item.ItemCodec.Category} name or null for all
     * @param search       case-insensitive match on material/display name, or null
     * @param sellerFilter only this seller's listings when set (the "my listings" view)
     */
    record BrowseQuery(int page, int pageSize, Sort sort,
                       String category, String search, UUID sellerFilter) {

        public static BrowseQuery firstPage(int pageSize) {
            return new BrowseQuery(0, pageSize, Sort.NEWEST, null, null, null);
        }
    }

    record BrowsePage(List<AuctionListing> listings, int totalCount, int page, int pageSize) {

        public int pageCount() {
            return Math.max(1, (totalCount + pageSize - 1) / pageSize);
        }
    }

    enum CreateStatus { SUCCESS, INSUFFICIENT_FUNDS, LIMIT_REACHED, ACCOUNT_NOT_FOUND, FAILED }

    record CreateResult(CreateStatus status, Optional<AuctionListing> listing) {

        public static CreateResult failure(CreateStatus status) {
            return new CreateResult(status, Optional.empty());
        }
    }

    enum PurchaseStatus {
        SUCCESS, NOT_FOUND, NO_LONGER_ACTIVE, INSUFFICIENT_FUNDS, SELF_PURCHASE,
        ACCOUNT_NOT_FOUND, FAILED
    }

    /** {@code buyerBalanceAfter} is -1 unless the purchase succeeded. */
    record PurchaseResult(PurchaseStatus status, long buyerBalanceAfter,
                          Optional<AuctionListing> listing) {

        public static PurchaseResult failure(PurchaseStatus status) {
            return new PurchaseResult(status, -1, Optional.empty());
        }
    }

    enum CancelStatus { SUCCESS, NOT_FOUND, NOT_OWNER, NO_LONGER_ACTIVE }

    /**
     * Creates a listing, charging the listing fee from the seller's account
     * in the same transaction (fee burned as AUCTION_FEE — a money sink,
     * GDD section 17).
     */
    CreateResult create(UUID sellerUuid, byte[] itemData, String summaryJson,
                        long price, long listingFee,
                        int durationHours, int maxActivePerSeller);

    /**
     * Atomic purchase (GDD section 22). Charges the buyer the full price,
     * credits the seller price minus {@code saleFee} (fee burned),
     * marks the listing SOLD and queues an AUCTION_ITEM delivery for the
     * buyer — all in one transaction.
     */
    PurchaseResult purchase(UUID listingId, UUID buyerUuid, long saleFee);

    /** Cancels an ACTIVE listing and queues the item back to the seller. */
    CancelStatus cancel(UUID listingId, UUID sellerUuid);

    /**
     * Marks every over-due ACTIVE listing EXPIRED and queues its item back
     * to the seller. Returns the number of listings expired.
     */
    int expireDue();

    BrowsePage browse(BrowseQuery query);

    Optional<AuctionListing> find(UUID listingId);
}
