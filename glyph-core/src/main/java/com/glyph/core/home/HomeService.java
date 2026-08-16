package com.glyph.core.home;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

public final class HomeService {

    public enum SetStatus { SAVED, RENAMED_EXISTING, LIMIT, BAD_NAME, NO_WORLD, DATABASE_DOWN }

    public enum DeleteStatus { DELETED, MISSING, BAD_NAME, DATABASE_DOWN }

    private final HomeRepository repository;
    private final BooleanSupplier databaseReady;
    private final Logger logger;

    public HomeService(HomeRepository repository, BooleanSupplier databaseReady, Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public SetStatus set(
            UUID playerUuid,
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
        Optional<String> name = HomeNames.normalize(rawName);
        if (name.isEmpty()) {
            return SetStatus.BAD_NAME;
        }
        if (world == null || world.isBlank()) {
            return SetStatus.NO_WORLD;
        }
        try {
            boolean replacing = repository.find(playerUuid, name.get()).isPresent();
            if (!replacing && repository.count(playerUuid) >= HomeNames.MAX_HOMES) {
                return SetStatus.LIMIT;
            }
            repository.upsert(new Home(
                    playerUuid,
                    "",
                    name.get(),
                    world,
                    x,
                    y,
                    z,
                    yaw,
                    pitch));
            return replacing ? SetStatus.RENAMED_EXISTING : SetStatus.SAVED;
        } catch (RuntimeException e) {
            logger.error("set home failed for {}", playerUuid, e);
            return SetStatus.DATABASE_DOWN;
        }
    }

    public Optional<Home> get(UUID playerUuid, String rawName) {
        if (!databaseReady.getAsBoolean()) {
            return Optional.empty();
        }
        Optional<String> name = HomeNames.normalize(rawName);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        try {
            return repository.find(playerUuid, name.get());
        } catch (RuntimeException e) {
            logger.error("get home failed for {}", playerUuid, e);
            return Optional.empty();
        }
    }

    public List<Home> list(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return List.of();
        }
        try {
            return repository.list(playerUuid);
        } catch (RuntimeException e) {
            logger.error("list homes failed for {}", playerUuid, e);
            return List.of();
        }
    }

    public DeleteStatus delete(UUID playerUuid, String rawName) {
        if (!databaseReady.getAsBoolean()) {
            return DeleteStatus.DATABASE_DOWN;
        }
        Optional<String> name = HomeNames.normalize(rawName);
        if (name.isEmpty()) {
            return DeleteStatus.BAD_NAME;
        }
        try {
            return repository.delete(playerUuid, name.get())
                    ? DeleteStatus.DELETED
                    : DeleteStatus.MISSING;
        } catch (RuntimeException e) {
            logger.error("delete home failed for {}", playerUuid, e);
            return DeleteStatus.DATABASE_DOWN;
        }
    }
}
