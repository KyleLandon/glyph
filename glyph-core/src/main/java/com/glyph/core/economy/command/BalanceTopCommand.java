package com.glyph.core.economy.command;

import com.glyph.api.economy.EconomyApi;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * {@code /baltop} — the ten largest balances.
 */
public final class BalanceTopCommand implements CommandExecutor {

    private static final int SIZE = 10;

    private final EconomyApi economy;
    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;

    public BalanceTopCommand(EconomyApi economy, SchedulerAdapter scheduler,
                             EconomySettings settings) {
        this.economy = economy;
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        economy.topBalances(SIZE).whenComplete((top, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, sender, Component.text(
                        "Leaderboard unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Top balances", NamedTextColor.GOLD));
            if (top.isEmpty()) {
                lines.add(Component.text("  No accounts yet.", NamedTextColor.GRAY));
            }
            for (int i = 0; i < top.size(); i++) {
                lines.add(Component.text("  #" + (i + 1) + " ", NamedTextColor.GRAY)
                        .append(Component.text(top.get(i).username(), NamedTextColor.WHITE))
                        .append(Component.text(" — ", NamedTextColor.GRAY))
                        .append(Component.text(
                                top.get(i).balance().format(settings.currencySymbol()),
                                NamedTextColor.GREEN)));
            }
            CommandFeedback.deliver(scheduler, sender, lines);
        });
        return true;
    }
}
