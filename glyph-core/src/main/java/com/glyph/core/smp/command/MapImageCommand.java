package com.glyph.core.smp.command;

import com.glyph.core.smp.imagemap.ImageMapService;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MapImageCommand implements CommandExecutor, TabCompleter {

    private final ImageMapService images;

    public MapImageCommand(ImageMapService images) {
        this.images = Objects.requireNonNull(images, "images");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can paint maps.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /mapimage <https://...>", NamedTextColor.RED));
            return true;
        }
        images.create(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        return List.of();
    }
}
