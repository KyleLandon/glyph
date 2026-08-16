package com.glyph.core.home.command;

import com.glyph.core.home.Home;
import com.glyph.core.home.HomeNames;
import com.glyph.core.home.HomeService;
import com.glyph.core.home.HomeService.DeleteStatus;
import com.glyph.core.home.HomeService.SetStatus;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Forever World homes: {@code /sethome}, {@code /home}, {@code /delhome},
 * {@code /homes}.
 */
public final class HomeCommand implements CommandExecutor, TabCompleter {

    private final HomeService homes;
    private final SchedulerAdapter scheduler;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public HomeCommand(HomeService homes, SchedulerAdapter scheduler) {
        this.homes = homes;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use homes.", NamedTextColor.RED));
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "sethome" -> set(player, args);
            case "delhome" -> delete(player, args);
            case "homes" -> list(player);
            default -> go(player, args);
        };
    }

    private boolean set(Player player, String[] args) {
        String raw = args.length == 0 ? HomeNames.DEFAULT : args[0];
        Location location = player.getLocation();
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        scheduler.runAsync(() -> {
            SetStatus status = homes.set(
                    player.getUniqueId(), raw, world,
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
            scheduler.runForEntity(player, () -> player.sendMessage(switch (status) {
                case SAVED -> Component.text("Home set. /home to return.", NamedTextColor.GREEN);
                case RENAMED_EXISTING -> Component.text("Home updated.", NamedTextColor.GREEN);
                case LIMIT -> Component.text(
                        "You already have " + HomeNames.MAX_HOMES + " homes. /delhome <name>",
                        NamedTextColor.RED);
                case BAD_NAME -> Component.text(
                        "Home names are letters, numbers, underscore. Max 16.",
                        NamedTextColor.RED);
                case NO_WORLD -> Component.text("Cannot set a home here.", NamedTextColor.RED);
                case DATABASE_DOWN -> Component.text("Homes are unavailable right now.",
                        NamedTextColor.RED);
            }), null);
        });
        return true;
    }

    private boolean go(Player player, String[] args) {
        String raw = args.length == 0 ? HomeNames.DEFAULT : args[0];
        if (!inFlight.add(player.getUniqueId())) {
            player.sendMessage(Component.text("Teleport already in progress.", NamedTextColor.YELLOW));
            return true;
        }
        scheduler.runAsync(() -> {
            try {
                var home = homes.get(player.getUniqueId(), raw);
                if (home.isEmpty()) {
                    scheduler.runForEntity(player, () -> player.sendMessage(Component.text(
                            "No home named '" + raw + "'. /sethome or /homes",
                            NamedTextColor.RED)), null);
                    return;
                }
                Location dest = home.get().toLocation();
                scheduler.runForEntity(player, () -> {
                    if (dest == null) {
                        player.sendMessage(Component.text("That home's world is not loaded.",
                                NamedTextColor.RED));
                        return;
                    }
                    player.teleportAsync(dest).thenAccept(ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            player.sendMessage(Component.text("Welcome home.", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
                        }
                    });
                }, null);
            } finally {
                inFlight.remove(player.getUniqueId());
            }
        });
        return true;
    }

    private boolean delete(Player player, String[] args) {
        String raw = args.length == 0 ? HomeNames.DEFAULT : args[0];
        scheduler.runAsync(() -> {
            DeleteStatus status = homes.delete(player.getUniqueId(), raw);
            scheduler.runForEntity(player, () -> player.sendMessage(switch (status) {
                case DELETED -> Component.text("Home deleted.", NamedTextColor.GREEN);
                case MISSING -> Component.text("No home named '" + raw + "'.", NamedTextColor.RED);
                case BAD_NAME -> Component.text("Not a valid home name.", NamedTextColor.RED);
                case DATABASE_DOWN -> Component.text("Homes are unavailable right now.",
                        NamedTextColor.RED);
            }), null);
        });
        return true;
    }

    private boolean list(Player player) {
        scheduler.runAsync(() -> {
            List<Home> owned = homes.list(player.getUniqueId());
            scheduler.runForEntity(player, () -> {
                if (owned.isEmpty()) {
                    player.sendMessage(Component.text(
                            "No homes yet. /sethome to drop one here.", NamedTextColor.GRAY));
                    return;
                }
                String names = String.join(", ", owned.stream().map(Home::name).toList());
                player.sendMessage(Component.text(
                        "Homes (" + owned.size() + "/" + HomeNames.MAX_HOMES + "): " + names,
                        NamedTextColor.GOLD));
            }, null);
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("home") && !name.equals("delhome")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Home home : homes.list(player.getUniqueId())) {
            if (home.name().startsWith(prefix)) {
                matches.add(home.name());
            }
        }
        return matches;
    }
}
