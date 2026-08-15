package com.glyph.core.discord.command;

import com.glyph.core.discord.DiscordLinkService;
import com.glyph.core.economy.command.CommandFeedback;
import com.glyph.core.scheduler.SchedulerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /unlinkdiscord} — remove the Discord link from this Minecraft account. */
public final class UnlinkDiscordCommand implements CommandExecutor {

    private final DiscordLinkService links;
    private final SchedulerAdapter scheduler;

    public UnlinkDiscordCommand(DiscordLinkService links, SchedulerAdapter scheduler) {
        this.links = links;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only players can unlink Discord (ops: /glyphadmin unlinkdiscord <player>).",
                    NamedTextColor.RED));
            return true;
        }
        links.unlink(player.getUniqueId()).thenAccept(removed -> {
            if (removed) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Discord unlinked. Prestige roles will no longer sync.",
                        NamedTextColor.GREEN));
            } else {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "No Discord account is linked.", NamedTextColor.YELLOW));
            }
        });
        return true;
    }
}
