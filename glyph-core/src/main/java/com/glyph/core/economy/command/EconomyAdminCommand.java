package com.glyph.core.economy.command;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.Money;
import com.glyph.api.player.PlayerApi;
import com.glyph.core.command.CommandTabs;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /eco <get|set|add|remove> <player> [amount]} — administrative
 * balance control (GDD section 18). Every mutation is ledgered as
 * ADMIN_ADJUSTMENT with the acting admin recorded and logged server-side.
 */
public final class EconomyAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("get", "set", "add", "remove");

    private final EconomyApi economy;
    private final PlayerApi players;
    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;

    public EconomyAdminCommand(EconomyApi economy, PlayerApi players,
                               SchedulerAdapter scheduler, EconomySettings settings) {
        this.economy = economy;
        this.players = players;
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            usage(sender, label);
            return true;
        }

        Money amount;
        if (sub.equals("get")) {
            amount = null;
        } else {
            if (args.length < 3) {
                usage(sender, label);
                return true;
            }
            try {
                amount = Money.parse(args[2]);
            } catch (IllegalArgumentException | ArithmeticException e) {
                sender.sendMessage(Component.text("Not a valid amount: " + args[2],
                        NamedTextColor.RED));
                return true;
            }
        }
        Money finalAmount = amount;

        PlayerNameResolver.resolve(players, args[1]).whenComplete((target, error) -> {
            if (error != null || target.isEmpty()) {
                CommandFeedback.deliver(scheduler, sender, Component.text(
                        error != null ? "Lookup failed — try again later."
                                : "Unknown player: " + args[1], NamedTextColor.RED));
                return;
            }
            UUID actor = sender instanceof Player admin ? admin.getUniqueId() : null;
            switch (sub) {
                case "get" -> get(sender, target.get());
                case "set" -> adjust(sender, target.get(), AdminOperation.SET, finalAmount, actor);
                case "add" -> adjust(sender, target.get(), AdminOperation.ADD, finalAmount, actor);
                case "remove" -> adjust(sender, target.get(), AdminOperation.REMOVE, finalAmount, actor);
                default -> throw new IllegalStateException(sub);
            }
        });
        return true;
    }

    private void get(CommandSender sender, PlayerNameResolver.PlayerRef target) {
        economy.balance(target.uuid()).whenComplete((balance, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, sender, Component.text(
                        "Balance unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            CommandFeedback.deliver(scheduler, sender, balance
                    .map(money -> Component.text(target.name() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(money.format(settings.currencySymbol()),
                                    NamedTextColor.GREEN)))
                    .orElse(Component.text(target.name() + " has no account.",
                            NamedTextColor.RED)));
        });
    }

    private void adjust(CommandSender sender, PlayerNameResolver.PlayerRef target,
                        AdminOperation operation, Money amount, UUID actor) {
        economy.adminAdjust(target.uuid(), operation, amount, actor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        CommandFeedback.deliver(scheduler, sender, Component.text(
                                "Adjustment failed — try again later.", NamedTextColor.RED));
                        return;
                    }
                    Component message = switch (result.status()) {
                        case SUCCESS -> Component.text(target.name() + " now has ",
                                        NamedTextColor.GREEN)
                                .append(Component.text(result.newBalance().orElse(Money.ZERO)
                                                .format(settings.currencySymbol()),
                                        NamedTextColor.GREEN));
                        case ACCOUNT_NOT_FOUND -> Component.text(
                                target.name() + " has no account.", NamedTextColor.RED);
                        case INSUFFICIENT_FUNDS -> Component.text(
                                target.name() + " does not have that much.", NamedTextColor.RED);
                        case INVALID_AMOUNT -> Component.text("Invalid amount.",
                                NamedTextColor.RED);
                        default -> Component.text("Adjustment failed — try again later.",
                                NamedTextColor.RED);
                    };
                    CommandFeedback.deliver(scheduler, sender, message);
                });
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Usage: /" + label + " <get|set|add|remove> <player> [amount]",
                NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            return CommandTabs.onlinePlayers(sender, args[1]);
        }
        return List.of();
    }
}
