package com.glyph.discord.commands;

import com.glyph.discord.DiscordBotConfig;
import com.glyph.discord.db.DiscordIdentityRepository;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;

public final class SlashCommandListener extends ListenerAdapter {

    private final DiscordBotConfig config;
    private final DiscordIdentityRepository repository;
    private final AccountLinkService links;
    private final Logger logger;

    public SlashCommandListener(
            DiscordBotConfig config,
            DiscordIdentityRepository repository,
            AccountLinkService links,
            Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.links = Objects.requireNonNull(links, "links");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "link" -> handleLink(event);
            case "alpha" -> handleAlpha(event);
            default -> {
            }
        }
    }

    private void handleLink(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        OptionMapping codeOption = event.getOption("code");
        if (codeOption == null) {
            event.getHook().sendMessage("Missing code.").queue();
            return;
        }
        AccountLinkService.LinkResult result = links.link(
                codeOption.getAsString(),
                event.getUser().getIdLong(),
                event.getGuild(),
                event.getMember());
        event.getHook().sendMessage(result.message()).queue();
    }

    private void handleAlpha(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        Member actor = event.getMember();
        if (actor == null || !actor.hasPermission(Permission.MANAGE_ROLES)) {
            event.getHook().sendMessage("You need **Manage Roles** to use `/alpha`.").queue();
            return;
        }
        if (!config.hasAlphaRole()) {
            event.getHook().sendMessage("Alpha role is not configured on the bot.").queue();
            return;
        }

        String sub = event.getSubcommandName();
        OptionMapping userOption = event.getOption("user");
        if (sub == null || userOption == null) {
            event.getHook().sendMessage("Usage: `/alpha grant|revoke @user`").queue();
            return;
        }
        User target = userOption.getAsUser();
        boolean grant = sub.equals("grant");

        try {
            Optional<DiscordIdentityRepository.LinkedAccount> link =
                    repository.findByDiscord(target.getIdLong());
            if (link.isEmpty()) {
                event.getHook().sendMessage(
                        target.getAsMention() + " must `/link` a Minecraft account first.").queue();
                return;
            }

            repository.setAlphaAccess(link.get().minecraftUuid(), grant);

            Guild guild = event.getGuild();
            if (guild != null) {
                Role alpha = guild.getRoleById(config.alphaRoleId());
                Member member = guild.getMember(target);
                if (alpha != null && member != null) {
                    if (grant) {
                        guild.addRoleToMember(member, alpha).reason("Glyph alpha grant").queue();
                    } else {
                        guild.removeRoleFromMember(member, alpha).reason("Glyph alpha revoke").queue();
                    }
                }
            }

            String username = repository.username(link.get().minecraftUuid()).orElse("unknown");
            event.getHook().sendMessage(
                    (grant ? "Granted" : "Revoked") + " Glyph Alpha for **" + username + "**.")
                    .queue();
        } catch (Exception e) {
            logger.error("Discord /alpha failed", e);
            event.getHook().sendMessage("Alpha update failed — try again later.").queue();
        }
    }
}
