package com.glyph.core.smp.shop;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.bukkit.block.Block;
import org.slf4j.Logger;

public final class ChestShopService {

    public enum CreateStatus { CREATED, EXISTS, DATABASE_DOWN }

    public enum DeleteStatus { DELETED, MISSING, DATABASE_DOWN }

    private final ChestShopRepository repository;
    private final BooleanSupplier databaseReady;
    private final Logger logger;
    private final Map<String, ChestShop> byBlock = new ConcurrentHashMap<>();

    public ChestShopService(
            ChestShopRepository repository, BooleanSupplier databaseReady, Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void loadCache() {
        if (!databaseReady.getAsBoolean()) {
            return;
        }
        try {
            byBlock.clear();
            for (ChestShop shop : repository.listAll()) {
                byBlock.put(key(shop.world(), shop.x(), shop.y(), shop.z()), shop);
            }
        } catch (RuntimeException e) {
            logger.error("load shop cache failed", e);
        }
    }

    public Optional<ChestShop> cached(Block block) {
        return Optional.ofNullable(byBlock.get(key(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ())));
    }

    public Optional<ChestShop> find(Block block) {
        Optional<ChestShop> cached = cached(block);
        if (cached.isPresent()) {
            return cached;
        }
        if (!databaseReady.getAsBoolean()) {
            return Optional.empty();
        }
        try {
            Optional<ChestShop> found = repository.findAt(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
            found.ifPresent(shop -> byBlock.put(
                    key(shop.world(), shop.x(), shop.y(), shop.z()), shop));
            return found;
        } catch (RuntimeException e) {
            logger.error("find shop failed at {},{},{}", block.getX(), block.getY(), block.getZ(), e);
            return Optional.empty();
        }
    }

    public CreateStatus create(ChestShop shop) {
        if (!databaseReady.getAsBoolean()) {
            return CreateStatus.DATABASE_DOWN;
        }
        try {
            String loc = key(shop.world(), shop.x(), shop.y(), shop.z());
            if (byBlock.containsKey(loc)
                    || repository.findAt(shop.world(), shop.x(), shop.y(), shop.z()).isPresent()) {
                return CreateStatus.EXISTS;
            }
            repository.insert(shop);
            byBlock.put(loc, shop);
            return CreateStatus.CREATED;
        } catch (RuntimeException e) {
            logger.error("create shop failed for {}", shop.ownerUuid(), e);
            return CreateStatus.DATABASE_DOWN;
        }
    }

    public DeleteStatus delete(UUID id, UUID ownerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return DeleteStatus.DATABASE_DOWN;
        }
        try {
            boolean deleted = repository.delete(id, ownerUuid);
            if (deleted) {
                byBlock.values().removeIf(shop -> shop.id().equals(id));
            }
            return deleted ? DeleteStatus.DELETED : DeleteStatus.MISSING;
        } catch (RuntimeException e) {
            logger.error("delete shop failed for {}", ownerUuid, e);
            return DeleteStatus.DATABASE_DOWN;
        }
    }

    private static String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}

