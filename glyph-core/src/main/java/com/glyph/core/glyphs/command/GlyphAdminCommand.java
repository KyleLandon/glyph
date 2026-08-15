package com.glyph.core.glyphs.command;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.player.PlayerApi;
import com.glyph.core.discord.DiscordLinkService;
import com.glyph.core.economy.command.CommandFeedback;
import com.glyph.core.economy.command.PlayerNameResolver;
import com.glyph.core.glyphs.GlyphsService;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.ArrayList;
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
 * {@code /glyphadmin} — ops-only Glyph balance control (docs/GLYPHS.md).
 */
public final class GlyphAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("get", "set", "add", "remove", "unlinkdiscord");

    private final GlyphsService glyphs;
    private final DiscordLinkService discordLinks;
    private final PlayerApi players;
    private final SchedulerAdapter scheduler;

    public GlyphAdminCommand(
            GlyphsService glyphs,
            DiscordLinkService discordLinks,
            PlayerApi players,
            SchedulerAdapter scheduler) {
        this.glyphs = glyphs;
        this.discordLinks = discordLinks;
        this.players = players;
        this.scheduler = scheduler;
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

        if (sub.equals("unlinkdiscord")) {
            PlayerNameResolver.resolve(players, args[1]).whenComplete((target, error) -> {
                if (error != null || target.isEmpty()) {
                    CommandFeedback.deliver(scheduler, sender, Component.text(
                            error != null ? "Lookup failed — try again later."
                                    : "Unknown player: " + args[1], NamedTextColor.RED));
                    return;
                }
                discordLinks.unlink(target.get().uuid()).thenAccept(removed -> {
                    CommandFeedback.deliver(scheduler, sender, Component.text(
                            removed
                                    ? "Unlinked Discord for " + target.get().name() + "."
                                    : target.get().name() + " has no Discord link.",
                            removed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                });
            });
            return true;
        }

        long amount;
        if (sub.equals("get")) {
            amount = 0L;
        } else {
            if (args.length < 3) {
                usage(sender, label);
                return true;
            }
            try {
                amount = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Not a valid amount: " + args[2],
                        NamedTextColor.RED));
                return true;
            }
            if (amount < 0) {
                sender.sendMessage(Component.text("Amount must be non-negative.", NamedTextColor.RED));
                return true;
            }
        }
        long finalAmount = amount;

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
        glyphs.balance(target.uuid()).whenComplete((balance, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, sender, Component.text(
                        "Glyphs unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            CommandFeedback.deliver(scheduler, sender, Component.text(target.name() + ": ",
                            NamedTextColor.GRAY)
                    .append(Component.text(glyphs.settings().symbol() + balance,
                            NamedTextColor.LIGHT_PURPLE)));
        });
    }

    private void adjust(CommandSender sender, PlayerNameResolver.PlayerRef target,
                        AdminOperation operation, long amount, UUID actor) {
        glyphs.adminAdjust(target.uuid(), operation, amount, actor)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        CommandFeedback.deliver(scheduler, sender, Component.text(
                                "Adjustment failed — try again later.", NamedTextColor.RED));
                        return;
                    }
                    Component message = switch (result.status()) {
                        case SUCCESS -> Component.text(target.name() + " now has ",
                                        NamedTextColor.GREEN)
                                .append(Component.text(glyphs.settings().symbol() + result.balance(),
                                        NamedTextColor.LIGHT_PURPLE));
                        case INSUFFICIENT -> Component.text(
                                target.name() + " does not have that many Glyphs.",
                                NamedTextColor.RED);
                        default -> Component.text("Adjustment failed — try again later.",
                                NamedTextColor.RED);
                    };
                    CommandFeedback.deliver(scheduler, sender, message);
                });
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text(
                "Usage: /" + label + " <get|set|add|remove|unlinkdiscord> <player> [amount]",
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
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player online : sender.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    names.add(online.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}
