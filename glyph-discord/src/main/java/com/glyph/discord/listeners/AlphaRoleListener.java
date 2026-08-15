package com.glyph.discord.listeners;

import com.glyph.discord.DiscordBotConfig;
import com.glyph.discord.db.DiscordIdentityRepository;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;

/** Mirrors the Glyph Alpha Discord role onto {@code player_access.alpha}. */
public final class AlphaRoleListener extends ListenerAdapter {

    private final DiscordBotConfig config;
    private final DiscordIdentityRepository repository;
    private final Logger logger;

    public AlphaRoleListener(
            DiscordBotConfig config, DiscordIdentityRepository repository, Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        if (!config.hasAlphaRole() || event.getGuild().getIdLong() != config.guildId()) {
            return;
        }
        boolean added = event.getRoles().stream().anyMatch(role -> role.getIdLong() == config.alphaRoleId());
        if (!added) {
            return;
        }
        setAlpha(event.getUser().getIdLong(), true);
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (!config.hasAlphaRole() || event.getGuild().getIdLong() != config.guildId()) {
            return;
        }
        boolean removed = event.getRoles().stream()
                .anyMatch(role -> role.getIdLong() == config.alphaRoleId());
        if (!removed) {
            return;
        }
        setAlpha(event.getUser().getIdLong(), false);
    }

    private void setAlpha(long discordUserId, boolean alpha) {
        try {
            Optional<DiscordIdentityRepository.LinkedAccount> link =
                    repository.findByDiscord(discordUserId);
            if (link.isEmpty()) {
                logger.info(
                        "Alpha role {} for unlinked Discord user {}",
                        alpha ? "added" : "removed",
                        discordUserId);
                return;
            }
            repository.setAlphaAccess(link.get().minecraftUuid(), alpha);
            logger.info(
                    "Set player_access.alpha={} for {} (discord {})",
                    alpha, link.get().minecraftUuid(), discordUserId);
        } catch (Exception e) {
            logger.error("Failed to update alpha access for Discord {}", discordUserId, e);
        }
    }
}
