package com.glyph.core.smp.warp;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

public final class WarpService {

    public enum SetStatus {
        CREATED, TAKEN, LIMIT, BAD_NAME, NO_WORLD, DATABASE_DOWN
    }

    public enum DeleteStatus { DELETED, MISSING, BAD_NAME, DATABASE_DOWN }

    private final WarpRepository repository;
    private final BooleanSupplier databaseReady;
    private final int maxPerPlayer;
    private final Logger logger;

    public WarpService(
            WarpRepository repository,
            BooleanSupplier databaseReady,
            int maxPerPlayer,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.maxPerPlayer = maxPerPlayer;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public SetStatus create(
            UUID owner,
            String rawName,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch) {
        if (!databaseReady.getAsBoolean()) {
            return SetStatus.DATABASE_DOWN;
        }
        Optional<String> name = WarpNames.normalize(rawName);
        if (name.isEmpty()) {
            return SetStatus.BAD_NAME;
        }
        if (world == null || world.isBlank()) {
            return SetStatus.NO_WORLD;
        }
        try {
            if (repository.find(name.get()).isPresent()) {
                return SetStatus.TAKEN;
            }
            if (repository.countOwned(owner) >= maxPerPlayer) {
                return SetStatus.LIMIT;
            }
            repository.insert(new PlayerWarp(
                    name.get(), owner, "", world, x, y, z, yaw, pitch));
            return SetStatus.CREATED;
        } catch (RuntimeException e) {
            if (isUniqueViolation(e)) {
                return SetStatus.TAKEN;
            }
            logger.error("create warp failed for {}", owner, e);
            return SetStatus.DATABASE_DOWN;
        }
    }

    public Optional<PlayerWarp> get(String rawName) {
        if (!databaseReady.getAsBoolean()) {
            return Optional.empty();
        }
        Optional<String> name = WarpNames.normalize(rawName);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        try {
            return repository.find(name.get());
        } catch (RuntimeException e) {
            logger.error("get warp failed for {}", rawName, e);
            return Optional.empty();
        }
    }

    public List<PlayerWarp> listAll() {
        if (!databaseReady.getAsBoolean()) {
            return List.of();
        }
        try {
            return repository.listAll();
        } catch (RuntimeException e) {
            logger.error("list warps failed", e);
            return List.of();
        }
    }

    public DeleteStatus delete(UUID owner, String rawName) {
        if (!databaseReady.getAsBoolean()) {
            return DeleteStatus.DATABASE_DOWN;
        }
        Optional<String> name = WarpNames.normalize(rawName);
        if (name.isEmpty()) {
            return DeleteStatus.BAD_NAME;
        }
        try {
            return repository.delete(name.get(), owner)
                    ? DeleteStatus.DELETED
                    : DeleteStatus.MISSING;
        } catch (RuntimeException e) {
            logger.error("delete warp failed for {}", owner, e);
            return DeleteStatus.DATABASE_DOWN;
        }
    }

    private static boolean isUniqueViolation(RuntimeException e) {
        Throwable cause = e.getCause();
        return cause instanceof SQLException sql && "23505".equals(sql.getSQLState());
    }
}
