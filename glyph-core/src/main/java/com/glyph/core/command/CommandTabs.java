package com.glyph.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Shared tab-complete helpers. Paper suggests player names when no completer is set. */
public final class CommandTabs {

    /** Use this instead of {@code null} so Paper does not invent player-name suggestions. */
    public static final TabCompleter NONE = (sender, command, label, args) -> List.of();

    private CommandTabs() {
    }

    public static List<String> onlinePlayers(CommandSender sender, String prefix) {
        String start = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player online : sender.getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(start)) {
                names.add(online.getName());
            }
        }
        return names;
    }
}
