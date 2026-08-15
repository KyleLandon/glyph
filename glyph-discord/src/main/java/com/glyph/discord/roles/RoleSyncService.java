package com.glyph.discord.roles;

import com.glyph.api.discord.DiscordTier;
import com.glyph.discord.DiscordBotConfig;
import com.glyph.discord.db.DiscordIdentityRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;

/** Assigns Verified + lifetime prestige roles for a linked Discord member. */
public final class RoleSyncService {

    private final DiscordBotConfig config;
    private final DiscordIdentityRepository repository;
    private final Logger logger;

    public RoleSyncService(
            DiscordBotConfig config, DiscordIdentityRepository repository, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void syncForMinecraft(Guild guild, UUID minecraftUuid) {
        try {
            Optional<DiscordIdentityRepository.LinkedAccount> link =
                    repository.findByMinecraft(minecraftUuid);
            if (link.isEmpty()) {
                return;
            }
            Member member = guild.getMemberById(link.get().discordUserId());
            if (member == null) {
                guild.retrieveMemberById(link.get().discordUserId())
                        .queue(
                                retrieved -> syncMember(guild, retrieved, minecraftUuid),
                                error -> logger.warn(
                                        "Could not retrieve Discord member {} for role sync: {}",
                                        link.get().discordUserId(), error.toString()));
                return;
            }
            syncMember(guild, member, minecraftUuid);
        } catch (Exception e) {
            logger.error("Role sync failed for {}", minecraftUuid, e);
        }
    }

    public void syncMember(Guild guild, Member member, UUID minecraftUuid) {
        try {
            long lifetime = repository.lifetimeGlyphsEarned(minecraftUuid);
            Optional<DiscordTier> earned = DiscordTier.forLifetimeEarned(lifetime);

            List<Role> toAdd = new ArrayList<>();
            List<Role> toRemove = new ArrayList<>();

            Role verified = guild.getRoleById(config.verifiedRoleId());
            if (verified != null && !member.getRoles().contains(verified)) {
                toAdd.add(verified);
            }

            for (DiscordTier tier : DiscordTier.values()) {
                Long roleId = config.tierRoleIds().get(tier);
                if (roleId == null || roleId == 0L) {
                    continue;
                }
                Role role = guild.getRoleById(roleId);
                if (role == null) {
                    continue;
                }
                boolean shouldHave = earned.isPresent() && tier == earned.get();
                boolean has = member.getRoles().contains(role);
                if (shouldHave && !has) {
                    toAdd.add(role);
                } else if (!shouldHave && has) {
                    toRemove.add(role);
                }
            }

            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                return;
            }
            guild.modifyMemberRoles(member, toAdd, toRemove)
                    .reason("Glyph prestige sync")
                    .queue(
                            success -> logger.info(
                                    "Synced Discord roles for {} (lifetime ✦{}, tier {})",
                                    minecraftUuid,
                                    lifetime,
                                    earned.map(DiscordTier::displayName).orElse("none")),
                            error -> logger.warn(
                                    "Failed to modify roles for {}: {}",
                                    minecraftUuid, error.toString()));
        } catch (Exception e) {
            logger.error("Role sync failed for member {} / {}", member.getId(), minecraftUuid, e);
        }
    }
}
