package com.glyph.core.economy.command;

import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Folia-safe message delivery from async completion threads: players are
 * messaged on their entity scheduler, console directly (it is thread-safe).
 */
final class CommandFeedback {

    private CommandFeedback() {
    }

    static void deliver(SchedulerAdapter scheduler, CommandSender sender, Component message) {
        deliver(scheduler, sender, List.of(message));
    }

    static void deliver(SchedulerAdapter scheduler, CommandSender sender, List<Component> messages) {
        if (sender instanceof Player player) {
            scheduler.runForEntity(player, () -> messages.forEach(player::sendMessage), null);
        } else {
            messages.forEach(sender::sendMessage);
        }
    }
}
