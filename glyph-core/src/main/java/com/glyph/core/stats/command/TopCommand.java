package com.glyph.core.stats.command;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TopBalance;
import com.glyph.core.bounty.BountyRepository.TargetTotal;
import com.glyph.core.bounty.BountyService;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.player.PlayerRepository.PlaytimeLeader;
import com.glyph.core.player.PlayerService;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.stats.StatLeader;
import com.glyph.core.stats.StatType;
import com.glyph.core.stats.StatsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /top <money|kills|deaths|playtime|bounty>} — network leaderboards
 * (GDD section 31).
 */
public final class TopCommand implements CommandExecutor, TabCompleter {

    private static final int SIZE = 10;

    private final EconomyApi economy;
    private final StatsService stats;
    private final PlayerService players;
    private final BountyService bounties;
    private final SchedulerAdapter scheduler;
    private final EconomySettings economySettings;

    public TopCommand(
            EconomyApi economy,
            StatsService stats,
            PlayerService players,
            BountyService bounties,
            SchedulerAdapter scheduler,
            EconomySettings economySettings) {
        this.economy = economy;
        this.stats = stats;
        this.players = players;
        this.bounties = bounties;
        this.scheduler = scheduler;
        this.economySettings = economySettings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            deliver(sender, Component.text(
                    "Usage: /" + label + " <money|kills|deaths|playtime|bounty>",
                    NamedTextColor.RED));
            return true;
        }
        Optional<Category> category = parseCategory(args[0]);
        if (category.isEmpty()) {
            deliver(sender, Component.text(
                    "Unknown category. Use: money, kills, deaths, playtime, bounty.",
                    NamedTextColor.RED));
            return true;
        }
        switch (category.get()) {
            case MONEY -> showMoney(sender);
            case KILLS -> showStat(sender, StatType.KILLS, "Top killers");
            case DEATHS -> showStat(sender, StatType.DEATHS, "Most deaths");
            case PLAYTIME -> showPlaytime(sender);
            case BOUNTY -> showBounty(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Category.tabNames().stream()
                    .filter(name -> name.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    static Optional<Category> parseCategory(String arg) {
        if (arg == null || arg.isBlank()) {
            return Optional.empty();
        }
        return switch (arg.toLowerCase(Locale.ROOT)) {
            case "money", "bal", "balance", "baltop" -> Optional.of(Category.MONEY);
            case "kills", "kill" -> Optional.of(Category.KILLS);
            case "deaths", "death" -> Optional.of(Category.DEATHS);
            case "playtime", "time" -> Optional.of(Category.PLAYTIME);
            case "bounty", "bounties", "wanted" -> Optional.of(Category.BOUNTY);
            default -> Optional.empty();
        };
    }

    enum Category {
        MONEY, KILLS, DEATHS, PLAYTIME, BOUNTY;

        static List<String> tabNames() {
            return List.of("money", "kills", "deaths", "playtime", "bounty");
        }
    }

    private void showMoney(CommandSender sender) {
        economy.topBalances(SIZE).whenComplete((top, error) -> {
            if (error != null) {
                deliver(sender, Component.text(
                        "Leaderboard unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Top balances", NamedTextColor.GOLD));
            if (top.isEmpty()) {
                lines.add(Component.text("  No accounts yet.", NamedTextColor.GRAY));
            }
            for (int i = 0; i < top.size(); i++) {
                TopBalance row = top.get(i);
                lines.add(rankLine(i + 1, row.username(),
                        Component.text(row.balance().format(economySettings.currencySymbol()),
                                NamedTextColor.GREEN)));
            }
            deliver(sender, lines);
        });
    }

    private void showStat(CommandSender sender, StatType type, String title) {
        stats.top(type, SIZE).whenComplete((top, error) -> {
            if (error != null) {
                deliver(sender, Component.text(
                        "Leaderboard unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text(title, NamedTextColor.GOLD));
            if (top.isEmpty()) {
                lines.add(Component.text("  No stats yet.", NamedTextColor.GRAY));
            }
            for (int i = 0; i < top.size(); i++) {
                StatLeader row = top.get(i);
                lines.add(rankLine(i + 1, row.username(),
                        Component.text(String.valueOf(row.value()), NamedTextColor.WHITE)));
            }
            deliver(sender, lines);
        });
    }

    private void showPlaytime(CommandSender sender) {
        players.topPlaytime(SIZE).whenComplete((top, error) -> {
            if (error != null) {
                deliver(sender, Component.text(
                        "Leaderboard unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Most playtime", NamedTextColor.GOLD));
            if (top.isEmpty()) {
                lines.add(Component.text("  No players yet.", NamedTextColor.GRAY));
            }
            for (int i = 0; i < top.size(); i++) {
                PlaytimeLeader row = top.get(i);
                lines.add(rankLine(i + 1, row.username(),
                        Component.text(StatsCommand.formatDuration(row.playtimeSeconds()),
                                NamedTextColor.GOLD)));
            }
            deliver(sender, lines);
        });
    }

    private void showBounty(CommandSender sender) {
        if (!bounties.settings().enabled()) {
            deliver(sender, Component.text(
                    "Bounties are disabled.", NamedTextColor.RED));
            return;
        }
        bounties.topTargets(SIZE).whenComplete((top, error) -> {
            if (error != null) {
                deliver(sender, Component.text(
                        "Leaderboard unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Most wanted", NamedTextColor.GOLD));
            if (top.isEmpty()) {
                lines.add(Component.text("  No active bounties.", NamedTextColor.GRAY));
            }
            for (int i = 0; i < top.size(); i++) {
                TargetTotal row = top.get(i);
                lines.add(rankLine(i + 1, row.targetName(),
                        Component.text(Money.of(row.total())
                                        .format(economySettings.currencySymbol()),
                                NamedTextColor.GOLD))
                        .append(Component.text(" (" + row.count() + " bounty(ies))",
                                NamedTextColor.DARK_GRAY)));
            }
            deliver(sender, lines);
        });
    }

    private static Component rankLine(int rank, String username, Component value) {
        return Component.text("  #" + rank + " ", NamedTextColor.GRAY)
                .append(Component.text(username, NamedTextColor.WHITE))
                .append(Component.text(" — ", NamedTextColor.GRAY))
                .append(value);
    }

    private void deliver(CommandSender sender, Component message) {
        deliver(sender, List.of(message));
    }

    private void deliver(CommandSender sender, List<Component> messages) {
        if (sender instanceof Player player) {
            scheduler.runForEntity(player, () -> messages.forEach(player::sendMessage), null);
        } else {
            messages.forEach(sender::sendMessage);
        }
    }
}
