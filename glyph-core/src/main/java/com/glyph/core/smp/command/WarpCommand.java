package com.glyph.core.smp.command;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TransactionType;
import com.glyph.api.economy.TransferResult;
import com.glyph.core.claims.GriefPreventionAccess;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.config.SmpSettings;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.economy.command.CommandFeedback;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.smp.warp.PlayerWarp;
import com.glyph.core.smp.warp.WarpNames;
import com.glyph.core.smp.warp.WarpService;
import com.glyph.core.smp.warp.WarpService.DeleteStatus;
import com.glyph.core.smp.warp.WarpService.SetStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
 * {@code /warp <name>}, {@code /warp set <name>}, {@code /warp delete <name>},
 * {@code /warps}.
 */
public final class WarpCommand implements CommandExecutor, TabCompleter {

    private final WarpService warps;
    private final EconomyService economy;
    private final SmpSettings smp;
    private final EconomySettings money;
    private final SchedulerAdapter scheduler;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public WarpCommand(
            WarpService warps,
            EconomyService economy,
            SmpSettings smp,
            EconomySettings money,
            SchedulerAdapter scheduler) {
        this.warps = Objects.requireNonNull(warps, "warps");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.smp = Objects.requireNonNull(smp, "smp");
        this.money = Objects.requireNonNull(money, "money");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use warps.", NamedTextColor.RED));
            return true;
        }
        if (command.getName().equalsIgnoreCase("warps") || args.length == 0
                || args[0].equalsIgnoreCase("list")) {
            return list(player);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "set" -> set(player, args);
            case "delete", "del", "remove" -> delete(player, args);
            default -> go(player, args[0]);
        };
    }

    private boolean list(Player player) {
        scheduler.runAsync(() -> {
            List<PlayerWarp> all = warps.listAll();
            scheduler.runForEntity(player, () -> {
                if (all.isEmpty()) {
                    player.sendMessage(Component.text(
                            "No warps yet. /warp set <name> costs "
                                    + Money.of(smp.warpCreateCost()).format(money.currencySymbol()),
                            NamedTextColor.GRAY));
                    return;
                }
                String names = String.join(", ", all.stream().map(PlayerWarp::name).toList());
                player.sendMessage(Component.text("Warps: " + names, NamedTextColor.GOLD));
            }, null);
        });
        return true;
    }

    private boolean set(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /warp set <name>", NamedTextColor.RED));
            return true;
        }
        if (GriefPreventionAccess.present() && !GriefPreventionAccess.canBuild(player, player.getLocation())) {
            player.sendMessage(Component.text("Set warps on land you can build on.",
                    NamedTextColor.RED));
            return true;
        }
        if (!inFlight.add(player.getUniqueId())) {
            player.sendMessage(Component.text("Warp already processing.", NamedTextColor.YELLOW));
            return true;
        }
        Location location = player.getLocation();
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        Money cost = Money.of(smp.warpCreateCost());
        economy.systemAdjust(
                player.getUniqueId(), AdminOperation.REMOVE, cost,
                TransactionType.SYSTEM_SINK, "warp create " + args[1])
                .whenComplete((result, error) -> {
                    if (error != null || result == null || !result.isSuccess()) {
                        inFlight.remove(player.getUniqueId());
                        TransferResult.Status status = result == null
                                ? TransferResult.Status.FAILED : result.status();
                        CommandFeedback.deliver(scheduler, player, switch (status) {
                            case INSUFFICIENT_FUNDS -> Component.text(
                                    "Need " + cost.format(money.currencySymbol())
                                            + " to list a warp.", NamedTextColor.RED);
                            default -> Component.text("Could not charge for the warp.",
                                    NamedTextColor.RED);
                        });
                        return;
                    }
                    scheduler.runAsync(() -> {
                        SetStatus status = warps.create(
                                player.getUniqueId(), args[1], world,
                                location.getX(), location.getY(), location.getZ(),
                                location.getYaw(), location.getPitch());
                        if (status != SetStatus.CREATED) {
                            economy.systemAdjust(
                                    player.getUniqueId(), AdminOperation.ADD, cost,
                                    TransactionType.SYSTEM_REWARD, "warp create refund");
                        }
                        CommandFeedback.deliver(scheduler, player, switch (status) {
                            case CREATED -> Component.text(
                                    "Warp set. Others can /warp " + WarpNames.normalize(args[1])
                                            .orElse(args[1].toLowerCase(Locale.ROOT)),
                                    NamedTextColor.GREEN);
                            case TAKEN -> Component.text("That warp name is taken. Refunded.",
                                    NamedTextColor.RED);
                            case LIMIT -> Component.text(
                                    "You already have " + smp.maxWarpsPerPlayer()
                                            + " warps. Refunded.", NamedTextColor.RED);
                            case BAD_NAME -> Component.text(
                                    "Warp names are letters, numbers, underscore. Max 16.",
                                    NamedTextColor.RED);
                            case NO_WORLD -> Component.text("Cannot set a warp here.",
                                    NamedTextColor.RED);
                            case DATABASE_DOWN -> Component.text(
                                    "Warps are unavailable right now. Refunded.",
                                    NamedTextColor.RED);
                        });
                        inFlight.remove(player.getUniqueId());
                    });
                });
        return true;
    }

    private boolean go(Player player, String rawName) {
        if (!inFlight.add(player.getUniqueId())) {
            player.sendMessage(Component.text("Teleport already in progress.", NamedTextColor.YELLOW));
            return true;
        }
        scheduler.runAsync(() -> {
            try {
                var warp = warps.get(rawName);
                if (warp.isEmpty()) {
                    scheduler.runForEntity(player, () -> player.sendMessage(Component.text(
                            "No warp named '" + rawName + "'. /warps", NamedTextColor.RED)), null);
                    return;
                }
                Location dest = warp.get().toLocation();
                scheduler.runForEntity(player, () -> {
                    if (dest == null) {
                        player.sendMessage(Component.text("That warp's world is not loaded.",
                                NamedTextColor.RED));
                        return;
                    }
                    player.teleportAsync(dest).thenAccept(ok -> {
                        if (Boolean.TRUE.equals(ok)) {
                            player.sendMessage(Component.text(
                                    "Warped to " + warp.get().name() + ".", NamedTextColor.GREEN));
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
        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /warp delete <name>", NamedTextColor.RED));
            return true;
        }
        scheduler.runAsync(() -> {
            DeleteStatus status = warps.delete(player.getUniqueId(), args[1]);
            scheduler.runForEntity(player, () -> player.sendMessage(switch (status) {
                case DELETED -> Component.text("Warp deleted.", NamedTextColor.GREEN);
                case MISSING -> Component.text(
                        "No warp named '" + args[1] + "' that you own.", NamedTextColor.RED);
                case BAD_NAME -> Component.text("Not a valid warp name.", NamedTextColor.RED);
                case DATABASE_DOWN -> Component.text("Warps are unavailable right now.",
                        NamedTextColor.RED);
            }), null);
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>(List.of("set", "delete", "list"));
            matches.removeIf(s -> !s.startsWith(prefix));
            for (PlayerWarp warp : warps.listAll()) {
                if (warp.name().startsWith(prefix)) {
                    matches.add(warp.name());
                }
            }
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("delete") && sender instanceof Player player) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (PlayerWarp warp : warps.listAll()) {
                if (warp.ownerUuid().equals(player.getUniqueId()) && warp.name().startsWith(prefix)) {
                    matches.add(warp.name());
                }
            }
            return matches;
        }
        return List.of();
    }
}
