package com.glyph.core.command;

import com.glyph.api.health.ComponentHealth;
import com.glyph.api.health.HealthReport;
import com.glyph.api.health.HealthStatus;
import com.glyph.core.GlyphCorePlugin;
import com.glyph.core.smp.market.MarketBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /glyph <status|version>} — platform administration.
 *
 * <p>Folia safety: health checks run on async I/O threads; results are
 * delivered back to players via their entity scheduler.</p>
 */
public final class GlyphCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("status", "version", "restart", "setspawn", "market");

    private final GlyphCorePlugin plugin;
    private final RestartCommand restart;

    public GlyphCommand(GlyphCorePlugin plugin, RestartCommand restart) {
        this.plugin = plugin;
        this.restart = restart;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "version" -> version(sender);
            case "restart" -> restart.startCountdown(sender);
            case "setspawn" -> setSpawn(sender);
            case "market" -> market(sender, args);
            default -> sender.sendMessage(Component.text(
                    "Usage: /" + label + " <status|version|restart|setspawn|market>",
                    NamedTextColor.RED));
        }
        return true;
    }

    private void setSpawn(CommandSender sender) {
        if (!plugin.settings().role().isSmp()) {
            sender.sendMessage(Component.text("Set spawn on the Forever World.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Stand where spawn should be.", NamedTextColor.RED));
            return;
        }
        player.getWorld().setSpawnLocation(player.getLocation());
        player.sendMessage(Component.text("World spawn set here. /spawn uses this.", NamedTextColor.GREEN));
    }

    private void market(CommandSender sender, String[] args) {
        if (!plugin.settings().role().isSmp()) {
            sender.sendMessage(Component.text("The market street is Forever World only.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Stand near spawn to build the market.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("build")) {
            sender.sendMessage(Component.text("Usage: /glyph market build", NamedTextColor.RED));
            return;
        }
        MarketBuilder.build(player);
    }

    private void version(CommandSender sender) {
        sender.sendMessage(Component.text("GlyphCore " + plugin.getPluginMeta().getVersion()
                + " — server id: " + plugin.settings().serverId(), NamedTextColor.GOLD));
    }

    private void status(CommandSender sender) {
        sender.sendMessage(Component.text("Checking infrastructure health...", NamedTextColor.GRAY));
        plugin.healthService().check().thenAccept(report -> deliver(sender, render(report)));
    }

    private List<Component> render(HealthReport report) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Glyph platform status: ", NamedTextColor.GOLD)
                .append(statusComponent(report.overall())));
        for (ComponentHealth component : report.components()) {
            Component line = Component.text("  " + component.component() + ": ", NamedTextColor.GRAY)
                    .append(statusComponent(component.status()));
            if (component.latencyMillis() >= 0) {
                line = line.append(Component.text(" (" + component.latencyMillis() + "ms)",
                        NamedTextColor.DARK_GRAY));
            }
            if (component.status() != HealthStatus.UP && !component.detail().isBlank()) {
                line = line.append(Component.text(" — " + component.detail(), NamedTextColor.DARK_GRAY));
            }
            lines.add(line);
        }
        return lines;
    }

    private Component statusComponent(HealthStatus status) {
        return switch (status) {
            case UP -> Component.text("UP", NamedTextColor.GREEN);
            case INITIALIZING -> Component.text("INITIALIZING", NamedTextColor.YELLOW);
            case DOWN -> Component.text("DOWN", NamedTextColor.RED);
        };
    }

    /**
     * Sends messages from an async context. Players are messaged on their
     * entity scheduler; the console audience is thread-safe.
     */
    private void deliver(CommandSender sender, List<Component> lines) {
        if (sender instanceof Player player) {
            plugin.schedulerAdapter().runForEntity(player,
                    () -> lines.forEach(player::sendMessage), null);
        } else {
            lines.forEach(sender::sendMessage);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("market")
                && "build".startsWith(args[1].toLowerCase(Locale.ROOT))) {
            return List.of("build");
        }
        return List.of();
    }
}
