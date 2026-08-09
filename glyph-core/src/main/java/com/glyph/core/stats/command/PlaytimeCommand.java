package com.glyph.core.stats.command;

import com.glyph.api.player.PlayerApi;
import com.glyph.core.scheduler.SchedulerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /playtime} — the caller's accumulated online time (GDD section 66). */
public final class PlaytimeCommand implements CommandExecutor {

    private final PlayerApi players;
    private final SchedulerAdapter scheduler;

    public PlaytimeCommand(PlayerApi players, SchedulerAdapter scheduler) {
        this.players = players;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players have playtime.", NamedTextColor.RED));
            return true;
        }
        players.byUuid(player.getUniqueId()).whenComplete((profile, error) -> {
            Component message = (error != null || profile.isEmpty())
                    ? Component.text("Playtime is unavailable right now.", NamedTextColor.RED)
                    : Component.text("Playtime: ", NamedTextColor.GRAY).append(Component.text(
                            StatsCommand.formatDuration(profile.get().playtimeSeconds()),
                            NamedTextColor.GOLD));
            scheduler.runForEntity(player, () -> player.sendMessage(message), null);
        });
        return true;
    }
}
