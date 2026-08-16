package com.glyph.core.chat.command;

import com.glyph.core.chat.ChatChannels;
import com.glyph.core.config.ChatSettings;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** {@code /l} — say something nearby (same as default chat). */
public final class LocalChatCommand implements CommandExecutor, TabCompleter {

    private final ChatSettings settings;

    public LocalChatCommand(ChatSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use local chat.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /l <message>", NamedTextColor.YELLOW));
            return true;
        }
        ChatChannels.sendLocal(
                player,
                player.displayName().colorIfAbsent(NamedTextColor.WHITE),
                Component.text(String.join(" ", args)),
                settings.localRadius());
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
