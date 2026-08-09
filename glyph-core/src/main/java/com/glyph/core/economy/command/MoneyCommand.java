package com.glyph.core.economy.command;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.time.Duration;
import java.time.Instant;
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
 * {@code /money history} — the player's recent ledger entries.
 */
public final class MoneyCommand implements CommandExecutor, TabCompleter {

    private static final int HISTORY_SIZE = 10;

    private final EconomyApi economy;
    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;

    public MoneyCommand(EconomyApi economy, SchedulerAdapter scheduler,
                        EconomySettings settings) {
        this.economy = economy;
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players have a money history.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("history")) {
            player.sendMessage(Component.text("Usage: /" + label + " history",
                    NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        economy.history(uuid, HISTORY_SIZE).whenComplete((entries, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "History unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Recent transactions", NamedTextColor.GOLD));
            if (entries.isEmpty()) {
                lines.add(Component.text("  Nothing yet.", NamedTextColor.GRAY));
            }
            for (LedgerEntry entry : entries) {
                lines.add(render(uuid, entry));
            }
            CommandFeedback.deliver(scheduler, player, lines);
        });
        return true;
    }

    private Component render(UUID self, LedgerEntry entry) {
        boolean outgoing = entry.sourceOwner().map(self::equals).orElse(false);
        String amount = entry.amount().format(settings.currencySymbol());
        String type = entry.type().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.text(outgoing ? "  - " + amount : "  + " + amount,
                        outgoing ? NamedTextColor.RED : NamedTextColor.GREEN)
                .append(Component.text(" " + type, NamedTextColor.GRAY))
                .append(entry.reason().isBlank()
                        ? Component.empty()
                        : Component.text(" (" + entry.reason() + ")", NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + relative(entry.createdAt()),
                        NamedTextColor.DARK_GRAY));
    }

    private static String relative(Instant then) {
        Duration age = Duration.between(then, Instant.now());
        if (age.toDays() > 0) {
            return age.toDays() + "d ago";
        }
        if (age.toHours() > 0) {
            return age.toHours() + "h ago";
        }
        if (age.toMinutes() > 0) {
            return age.toMinutes() + "m ago";
        }
        return "just now";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 1 && "history".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("history");
        }
        return List.of();
    }
}
