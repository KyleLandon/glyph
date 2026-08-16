package com.glyph.core.stats.command;

import com.glyph.api.player.PlayerApi;
import com.glyph.api.player.PlayerProfile;
import com.glyph.core.command.CommandTabs;
import com.glyph.core.economy.command.PlayerNameResolver;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.stats.PlayerStats;
import com.glyph.core.stats.StatsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /stats [player]} — aggregated player statistics (GDD sections 30,
 * 66). Pending buffered deltas are flushed before the read, so numbers are
 * always current.
 */
public final class StatsCommand implements CommandExecutor, TabCompleter {

    private final StatsService stats;
    private final PlayerApi players;
    private final SchedulerAdapter scheduler;

    public StatsCommand(StatsService stats, PlayerApi players, SchedulerAdapter scheduler) {
        this.stats = stats;
        this.players = players;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String targetName;
        if (args.length >= 1) {
            targetName = args[0];
        } else if (sender instanceof Player player) {
            targetName = player.getName();
        } else {
            sender.sendMessage(Component.text("Usage: /" + label + " <player>",
                    NamedTextColor.RED));
            return true;
        }

        PlayerNameResolver.resolve(players, targetName)
                .thenCompose(target -> {
                    if (target.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of((Component)
                                Component.text("Unknown player: " + targetName,
                                        NamedTextColor.RED)));
                    }
                    CompletableFuture<Optional<PlayerStats>> statsFuture =
                            stats.stats(target.get().uuid());
                    CompletableFuture<Optional<PlayerProfile>> profileFuture =
                            players.byUuid(target.get().uuid());
                    return statsFuture.thenCombine(profileFuture, (playerStats, profile) ->
                            render(target.get().name(),
                                    playerStats.orElse(PlayerStats.empty(target.get().uuid())),
                                    profile));
                })
                .whenComplete((lines, error) -> deliver(sender, error != null
                        ? List.of(Component.text("Statistics are unavailable right now.",
                                NamedTextColor.RED))
                        : lines));
        return true;
    }

    private static List<Component> render(
            String name, PlayerStats playerStats, Optional<PlayerProfile> profile) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Stats for " + name, NamedTextColor.GOLD));
        profile.ifPresent(p -> {
            lines.add(row("Playtime", formatDuration(p.playtimeSeconds())));
            lines.add(row("First join", p.firstJoin().toString().substring(0, 10)));
        });
        lines.add(row("Kills", String.valueOf(playerStats.kills())));
        lines.add(row("Deaths", String.valueOf(playerStats.deaths())));
        lines.add(row("K/D", String.format(Locale.ROOT, "%.2f",
                playerStats.killDeathRatio())));
        lines.add(row("Bounties claimed", String.valueOf(playerStats.bountiesClaimed())));
        lines.add(row("Mob kills", String.valueOf(playerStats.mobKills())));
        lines.add(row("Blocks broken", String.valueOf(playerStats.blocksBroken())));
        lines.add(row("Blocks placed", String.valueOf(playerStats.blocksPlaced())));
        lines.add(row("Distance", String.format(Locale.ROOT, "%.1f km",
                playerStats.distanceCm() / 100_000.0)));
        lines.add(row("Auction sales", String.valueOf(playerStats.auctionSales())));
        lines.add(row("Auction purchases", String.valueOf(playerStats.auctionPurchases())));
        return lines;
    }

    private static Component row(String key, String value) {
        return Component.text(" " + key + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    static String formatDuration(long seconds) {
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private void deliver(CommandSender sender, List<Component> messages) {
        if (sender instanceof Player player) {
            scheduler.runForEntity(player, () -> messages.forEach(player::sendMessage), null);
        } else {
            messages.forEach(sender::sendMessage);
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CommandTabs.onlinePlayers(sender, args[0]);
        }
        return List.of();
    }
}
