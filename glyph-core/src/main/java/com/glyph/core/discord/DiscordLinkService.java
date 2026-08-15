package com.glyph.core.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

public final class DiscordLinkService {

    public static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final DiscordLinkRepository repository;
    private final BooleanSupplier databaseReady;
    private final Executor ioExecutor;
    private final Logger logger;

    public DiscordLinkService(
            DiscordLinkRepository repository,
            BooleanSupplier databaseReady,
            Executor ioExecutor,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.databaseReady = Objects.requireNonNull(databaseReady, "databaseReady");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CompletableFuture<IssueResult> issueCode(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(IssueResult.unavailable());
        }
        return CompletableFuture.supplyAsync(() -> {
            Optional<DiscordLinkRepository.LinkedAccount> existing =
                    repository.findByMinecraft(playerUuid);
            if (existing.isPresent()) {
                return IssueResult.alreadyLinked(existing.get().discordUserId());
            }
            String code = repository.issueCode(playerUuid, Instant.now().plus(CODE_TTL));
            logger.info("Issued Discord link code for {}", playerUuid);
            return IssueResult.code(code);
        }, ioExecutor).exceptionally(error -> {
            logger.error("Failed to issue Discord link code for {}", playerUuid, error);
            return IssueResult.unavailable();
        });
    }

    public CompletableFuture<Boolean> unlink(UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            boolean removed = repository.deleteLink(playerUuid);
            if (removed) {
                logger.info("Unlinked Discord for {}", playerUuid);
            }
            return removed;
        }, ioExecutor).exceptionally(error -> {
            logger.error("Failed to unlink Discord for {}", playerUuid, error);
            return false;
        });
    }

    public CompletableFuture<Optional<DiscordLinkRepository.LinkedAccount>> linkedAccount(
            UUID playerUuid) {
        if (!databaseReady.getAsBoolean()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(
                () -> repository.findByMinecraft(playerUuid), ioExecutor);
    }

    public sealed interface IssueResult {
        static IssueResult code(String code) {
            return new Code(code);
        }

        static IssueResult alreadyLinked(long discordUserId) {
            return new AlreadyLinked(discordUserId);
        }

        static IssueResult unavailable() {
            return Unavailable.INSTANCE;
        }

        record Code(String code) implements IssueResult {
        }

        record AlreadyLinked(long discordUserId) implements IssueResult {
        }

        enum Unavailable implements IssueResult {
            INSTANCE
        }
    }
}
