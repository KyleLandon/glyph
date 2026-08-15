package com.glyph.core.glyphs;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

/** Glyph shop purchases — see {@code docs/GLYPHS.md}. */
public final class GlyphShopService {

    public enum BuyStatus {
        SUCCESS,
        DISABLED,
        UNKNOWN_PRODUCT,
        ALREADY_OWNED,
        INSUFFICIENT,
        UNAVAILABLE
    }

    public record BuyOutcome(BuyStatus status, GlyphProduct product) {
        static BuyOutcome of(BuyStatus status) {
            return new BuyOutcome(status, null);
        }
    }

    private final GlyphsService glyphs;
    private final Logger logger;

    public GlyphShopService(GlyphsService glyphs, Logger logger) {
        this.glyphs = Objects.requireNonNull(glyphs, "glyphs");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<BuyOutcome> buy(UUID playerUuid, String productId, UUID actor) {
        if (!glyphs.settings().enabled()) {
            return CompletableFuture.completedFuture(BuyOutcome.of(BuyStatus.DISABLED));
        }
        Optional<GlyphProduct> productOpt = GlyphCatalog.find(productId);
        if (productOpt.isEmpty()) {
            return CompletableFuture.completedFuture(BuyOutcome.of(BuyStatus.UNKNOWN_PRODUCT));
        }
        GlyphProduct product = productOpt.get();
        if (product.type() == GlyphProductType.NAME_COLOR
                || product.type() == GlyphProductType.TITLE
                || product.type() == GlyphProductType.DEATH_MESSAGE) {
            return glyphs.hasUnlock(playerUuid, product.id()).thenCompose(owned -> {
                if (owned) {
                    return CompletableFuture.completedFuture(BuyOutcome.of(BuyStatus.ALREADY_OWNED));
                }
                return completePurchase(playerUuid, product, actor);
            });
        }
        return CompletableFuture.completedFuture(BuyOutcome.of(BuyStatus.UNKNOWN_PRODUCT));
    }

    private CompletableFuture<BuyOutcome> completePurchase(
            UUID playerUuid, GlyphProduct product, UUID actor) {
        return glyphs.debitPurchase(playerUuid, product.cost(), product.id(), actor)
                .thenCompose(newBalance -> {
                    if (newBalance.isEmpty()) {
                        return CompletableFuture.completedFuture(BuyOutcome.of(BuyStatus.INSUFFICIENT));
                    }
                    return glyphs.recordUnlock(playerUuid, product.id())
                            .thenCompose(ignored -> switch (product.type()) {
                                case NAME_COLOR -> glyphs.equipColor(playerUuid, product.id())
                                        .thenApply(result -> success(playerUuid, product));
                                case TITLE -> glyphs.equipTitle(playerUuid, product.id())
                                        .thenApply(result -> success(playerUuid, product));
                                case DEATH_MESSAGE -> glyphs.equipDeathStyle(playerUuid, product.id())
                                        .thenApply(result -> success(playerUuid, product));
                            });
                })
                .exceptionally(error -> {
                    logger.error("Glyph purchase failed: {} / {}", playerUuid, product.id(), error);
                    return BuyOutcome.of(BuyStatus.UNAVAILABLE);
                });
    }

    private BuyOutcome success(UUID playerUuid, GlyphProduct product) {
        logger.info("Glyph purchase: {} bought {} for {} glyphs",
                playerUuid, product.id(), product.cost());
        return new BuyOutcome(BuyStatus.SUCCESS, product);
    }
}
