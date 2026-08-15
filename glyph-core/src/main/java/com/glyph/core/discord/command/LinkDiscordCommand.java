package com.glyph.core.discord.command;

import com.glyph.core.config.DiscordSettings;
import com.glyph.core.discord.DiscordLinkService;
import com.glyph.core.economy.command.CommandFeedback;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /linkdiscord} — issue a one-time Discord verification code. */
public final class LinkDiscordCommand implements CommandExecutor {

    private final DiscordLinkService links;
    private final DiscordSettings discord;
    private final SchedulerAdapter scheduler;

    public LinkDiscordCommand(
            DiscordLinkService links, DiscordSettings discord, SchedulerAdapter scheduler) {
        this.links = links;
        this.discord = discord;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can link Discord.", NamedTextColor.RED));
            return true;
        }
        links.issueCode(player.getUniqueId()).thenAccept(result -> {
            if (result instanceof DiscordLinkService.IssueResult.Unavailable) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Linking is temporarily unavailable. Try again shortly.",
                        NamedTextColor.RED));
                return;
            }
            if (result instanceof DiscordLinkService.IssueResult.AlreadyLinked linked) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Already linked to Discord user " + linked.discordUserId()
                                + ". Use /unlinkdiscord first to change accounts.",
                        NamedTextColor.YELLOW));
                return;
            }
            if (result instanceof DiscordLinkService.IssueResult.Code issued) {
                Component code = Component.text(issued.code(), NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(issued.code()))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Click to copy", NamedTextColor.GRAY)));
                Component linkCmd = Component.text("/link " + issued.code(), NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard("/link " + issued.code()))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Click to copy Discord command", NamedTextColor.GRAY)));
                Component invite = Component.text(discord.inviteUrl(), NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(discord.inviteUrl()))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Open Discord invite", NamedTextColor.GRAY)));
                CommandFeedback.deliver(scheduler, player, List.of(
                        Component.text("Your verification code is:", NamedTextColor.GRAY),
                        code,
                        Component.text("Run ", NamedTextColor.GRAY)
                                .append(linkCmd)
                                .append(Component.text(" in the Glyph Discord.", NamedTextColor.GRAY)),
                        Component.text("Expires in 10 minutes.", NamedTextColor.DARK_GRAY),
                        Component.text("Invite: ", NamedTextColor.DARK_GRAY).append(invite)));
            }
        });
        return true;
    }
}
