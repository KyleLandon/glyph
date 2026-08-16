package com.glyph.core.chat.command;

import com.glyph.core.chat.ChatChannels;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /g} — shout to everyone on this world. */
public final class GlobalChatCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use global chat.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /g <message>", NamedTextColor.YELLOW));
            return true;
        }
        ChatChannels.sendGlobal(
                player,
                player.displayName().colorIfAbsent(NamedTextColor.WHITE),
                Component.text(String.join(" ", args)));
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
