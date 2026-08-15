package com.glyph.discord.commands;

import com.glyph.api.event.GlyphEventChannels;
import com.glyph.api.event.GlyphEventCodec;
import com.glyph.discord.db.DiscordIdentityRepository;
import com.glyph.discord.roles.RoleSyncService;
import io.lettuce.core.api.sync.RedisCommands;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

/** Shared Minecraft↔Discord link logic for slash and text commands. */
public final class AccountLinkService {

    private final DiscordIdentityRepository repository;
    private final RoleSyncService roleSync;
    private final RedisCommands<String, String> redis;
    private final Logger logger;

    public AccountLinkService(
            DiscordIdentityRepository repository,
            RoleSyncService roleSync,
            RedisCommands<String, String> redis,
            Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.roleSync = Objects.requireNonNull(roleSync, "roleSync");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public LinkResult link(String rawCode, long discordUserId, Guild guild, Member member) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isBlank()) {
            return LinkResult.fail("Missing code. Usage: `/link GLYPH-XXXXXX`");
        }
        try {
            Optional<DiscordIdentityRepository.LinkedAccount> already =
                    repository.findByDiscord(discordUserId);
            if (already.isPresent()) {
                String name = repository.username(already.get().minecraftUuid()).orElse("unknown");
                return LinkResult.fail(
                        "Already linked to **" + name
                                + "**. Unlink in-game with `/unlinkdiscord` first.");
            }

            Optional<UUID> peeked = repository.findValidCode(code, Instant.now());
            if (peeked.isEmpty()) {
                return LinkResult.fail(
                        "Invalid or expired code. Run `/linkdiscord` in-game for a new one.");
            }

            Optional<DiscordIdentityRepository.LinkedAccount> mcLinked =
                    repository.findByMinecraft(peeked.get());
            if (mcLinked.isPresent() && mcLinked.get().discordUserId() != discordUserId) {
                return LinkResult.fail(
                        "That Minecraft account is already linked to a different Discord user.");
            }

            Optional<UUID> minecraftUuid;
            try {
                minecraftUuid = repository.consumeCodeAndLink(code, discordUserId, Instant.now());
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    return LinkResult.fail(
                            "That Discord account or Minecraft account is already linked.");
                }
                throw e;
            }
            if (minecraftUuid.isEmpty()) {
                return LinkResult.fail(
                        "Invalid or expired code. Run `/linkdiscord` in-game for a new one.");
            }

            String username = repository.username(minecraftUuid.get())
                    .orElse(minecraftUuid.get().toString());
            redis.publish(
                    GlyphEventChannels.EVENTS,
                    GlyphEventCodec.discordLinked(minecraftUuid.get(), discordUserId));

            if (guild != null) {
                if (member != null) {
                    roleSync.syncMember(guild, member, minecraftUuid.get());
                } else {
                    roleSync.syncForMinecraft(guild, minecraftUuid.get());
                }
            }

            logger.info(
                    "Linked Discord {} to Minecraft {} ({})",
                    discordUserId, minecraftUuid.get(), username);
            return LinkResult.ok("✓ Linked to **" + username + "**");
        } catch (Exception e) {
            logger.error("Discord /link failed for {}", discordUserId, e);
            return LinkResult.fail("Link failed — try again later.");
        }
    }

    public record LinkResult(boolean success, String message) {
        static LinkResult ok(String message) {
            return new LinkResult(true, message);
        }

        static LinkResult fail(String message) {
            return new LinkResult(false, message);
        }
    }
}
