package com.glyph.core.nick.command;

import com.glyph.core.config.ChatSettings;
import com.glyph.core.nick.NicknameService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Nearby roleplay emote: {@code * Name waves}. Same range as local chat.
 */
public final class MeCommand implements CommandExecutor {

    private final NicknameService nicknames;
    private final double range;

    public MeCommand(NicknameService nicknames, ChatSettings chat) {
        this.nicknames = nicknames;
        this.range = chat.localRadius() > 0 ? chat.localRadius() : 100.0;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can /me.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /me <action>", NamedTextColor.YELLOW));
            return true;
        }
        String action = String.join(" ", args).trim();
        if (action.isEmpty()) {
            player.sendMessage(Component.text("Usage: /me <action>", NamedTextColor.YELLOW));
            return true;
        }
        String name = nicknames.visibleName(player.getUniqueId(), player.getName());
        Component line = Component.text("* ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(name, NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC))
                .append(Component.text(" " + action, NamedTextColor.LIGHT_PURPLE, TextDecoration.ITALIC));
        Location origin = player.getLocation();
        double rangeSq = range * range;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())
                    || sameWorldNear(origin, other.getLocation(), rangeSq)) {
                other.sendMessage(line);
            }
        }
        return true;
    }

    private static boolean sameWorldNear(Location origin, Location other, double rangeSq) {
        if (origin.getWorld() == null || other.getWorld() == null) {
            return false;
        }
        if (!origin.getWorld().equals(other.getWorld())) {
            return false;
        }
        return origin.distanceSquared(other) <= rangeSq;
    }
}
