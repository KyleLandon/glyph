package com.glyph.core.nick;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.slf4j.Logger;

public final class NicknameService {

    public enum SetStatus { SAVED, BAD_NAME, TAKEN, DATABASE_DOWN }

    public enum ClearStatus { CLEARED, NONE, DATABASE_DOWN }

    private final NicknameRepository repository;
    private final BooleanSupplier databaseReady;
    private final Function<String, Optional<UUID>> onlineUsernameOwner;
    private final Logger logger;
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public NicknameService(
            NicknameRepository repository,
            BooleanSupplier databaseReady,
            Function<String, Optional<UUID>> onlineUsernameOwner,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.onlineUsernameOwner = Objects.requireNonNull(onlineUsernameOwner, "onlineUsernameOwner");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Optional<String> nickname(UUID playerUuid) {
        return Optional.ofNullable(cache.get(playerUuid));
    }

    public String visibleName(UUID playerUuid, String username) {
        String nick = cache.get(playerUuid);
        return (nick == null || nick.isBlank()) ? username : nick;
    }

    public void load(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return;
        }
        try {
            repository.find(playerUuid).ifPresentOrElse(
                    nick -> cache.put(playerUuid, nick),
                    () -> cache.remove(playerUuid));
        } catch (RuntimeException e) {
            logger.error("load nickname failed for {}", playerUuid, e);
        }
    }

    public void evict(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    public SetStatus set(UUID playerUuid, String raw) {
        if (!databaseReady.getAsBoolean()) {
            return SetStatus.DATABASE_DOWN;
        }
        Optional<String> name = NicknameNames.normalize(raw);
        if (name.isEmpty()) {
            return SetStatus.BAD_NAME;
        }
        if (takenBySomeoneElse(playerUuid, name.get())) {
            return SetStatus.TAKEN;
        }
        try {
            repository.upsert(playerUuid, name.get());
            cache.put(playerUuid, name.get());
            return SetStatus.SAVED;
        } catch (RuntimeException e) {
            if (takenBySomeoneElse(playerUuid, name.get())) {
                return SetStatus.TAKEN;
            }
            logger.error("set nickname failed for {}", playerUuid, e);
            return SetStatus.DATABASE_DOWN;
        }
    }

    public ClearStatus clear(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return ClearStatus.DATABASE_DOWN;
        }
        try {
            boolean deleted = repository.delete(playerUuid);
            cache.remove(playerUuid);
            return deleted ? ClearStatus.CLEARED : ClearStatus.NONE;
        } catch (RuntimeException e) {
            logger.error("clear nickname failed for {}", playerUuid, e);
            return ClearStatus.DATABASE_DOWN;
        }
    }

    private boolean takenBySomeoneElse(UUID playerUuid, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Optional<UUID> online = onlineUsernameOwner.apply(key);
        if (online.isPresent() && !online.get().equals(playerUuid)) {
            return true;
        }
        try {
            Optional<UUID> owner = repository.findOwner(name);
            return owner.isPresent() && !owner.get().equals(playerUuid);
        } catch (RuntimeException e) {
            logger.error("nickname taken check failed", e);
            return true;
        }
    }
}
