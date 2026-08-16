package com.glyph.core.economy.command;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.player.PlayerApi;
import com.glyph.core.command.CommandTabs;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /balance} ({@code /bal}) — own balance; {@code /balance <player>}
 * needs the admin permission.
 */
public final class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EconomyApi economy;
    private final PlayerApi players;
    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;

    public BalanceCommand(EconomyApi economy, PlayerApi players,
                          SchedulerAdapter scheduler, EconomySettings settings) {
        this.economy = economy;
        this.players = players;
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console usage: /balance <player>",
                        NamedTextColor.RED));
                return true;
            }
            show(sender, player.getUniqueId(), "Balance");
            return true;
        }

        if (!sender.hasPermission("glyph.economy.admin")) {
            sender.sendMessage(Component.text("You may only check your own balance.",
                    NamedTextColor.RED));
            return true;
        }
        PlayerNameResolver.resolve(players, args[0]).whenComplete((target, error) -> {
            if (error != null || target.isEmpty()) {
                CommandFeedback.deliver(scheduler, sender, Component.text(
                        error != null ? "Lookup failed — try again later."
                                : "Unknown player: " + args[0], NamedTextColor.RED));
                return;
            }
            show(sender, target.get().uuid(), target.get().name());
        });
        return true;
    }

    private void show(CommandSender sender, java.util.UUID uuid, String heading) {
        economy.balance(uuid).whenComplete((balance, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, sender, Component.text(
                        "Balance unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            Component message = balance
                    .map(money -> Component.text(heading + ": ", NamedTextColor.GRAY)
                            .append(Component.text(money.format(settings.currencySymbol()),
                                    NamedTextColor.GREEN)))
                    .orElse(Component.text("No account found.", NamedTextColor.RED));
            CommandFeedback.deliver(scheduler, sender, List.of(message));
        });
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("glyph.economy.admin")) {
            return CommandTabs.onlinePlayers(sender, args[0]);
        }
        return List.of();
    }
}
