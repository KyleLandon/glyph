package com.glyph.core.chat;

import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Local (nearby) vs global chat on one backend. */
public final class ChatChannels {

    public static final String LOCAL = "Local";
    public static final String GLOBAL = "Global";

    private ChatChannels() {
    }

    public static boolean inRange(Location from, Location to, double range) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return false;
        }
        if (!from.getWorld().equals(to.getWorld())) {
            return false;
        }
        if (range <= 0) {
            return true;
        }
        return from.distanceSquared(to) <= range * range;
    }

    public static boolean canHear(Player speaker, Player listener, double range) {
        return speaker.getUniqueId().equals(listener.getUniqueId())
                || inRange(speaker.getLocation(), listener.getLocation(), range);
    }

    public static Component line(String channel, NamedTextColor channelColor, Component name, Component body) {
        return Component.text("[" + channel + "] ", channelColor)
                .append(name.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(body);
    }

    public static Component localLine(Component name, Component body) {
        return line(LOCAL, NamedTextColor.DARK_GREEN, name, body);
    }

    public static Component globalLine(Component name, Component body) {
        return line(GLOBAL, NamedTextColor.GOLD, name, body);
    }

    public static void sendLocal(Player speaker, Component name, Component body, double range) {
        Component message = localLine(name, body);
        for (Player listener : speaker.getWorld().getPlayers()) {
            if (canHear(speaker, listener, range)) {
                listener.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public static void sendGlobal(Player speaker, Component name, Component body) {
        Objects.requireNonNull(speaker, "speaker");
        Bukkit.getServer().sendMessage(globalLine(name, body));
    }

    public static boolean shouldKeepViewer(Audience audience, Player speaker, double range) {
        if (!(audience instanceof Player listener)) {
            return true;
        }
        return canHear(speaker, listener, range);
    }
}
