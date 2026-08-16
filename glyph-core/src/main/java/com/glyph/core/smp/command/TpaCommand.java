package com.glyph.core.smp.command;

import com.glyph.core.command.CommandTabs;
import com.glyph.core.config.SmpSettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.smp.tpa.TpaService;
import com.glyph.core.smp.tpa.TpaService.Kind;
import com.glyph.core.smp.tpa.TpaService.Request;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * {@code /tpa}, {@code /tpahere}, {@code /tpaccept}, {@code /tpdeny}.
 */
public final class TpaCommand implements CommandExecutor, TabCompleter, Listener {

    private final TpaService tpa;
    private final SmpSettings settings;
    private final SchedulerAdapter scheduler;

    public TpaCommand(TpaService tpa, SmpSettings settings, SchedulerAdapter scheduler) {
        this.tpa = Objects.requireNonNull(tpa, "tpa");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport.", NamedTextColor.RED));
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpahere" -> request(player, args, Kind.HERE);
            case "tpaccept" -> accept(player);
            case "tpdeny" -> deny(player);
            default -> request(player, args, Kind.THERE);
        };
    }

    private boolean request(Player from, String[] args, Kind kind) {
        if (args.length != 1) {
            from.sendMessage(Component.text(
                    "Usage: /" + (kind == Kind.HERE ? "tpahere" : "tpa") + " <player>",
                    NamedTextColor.RED));
            return true;
        }
        Player to = Bukkit.getPlayerExact(args[0]);
        if (to == null || !to.isOnline()) {
            from.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return true;
        }
        Instant expires = Instant.now().plusSeconds(settings.tpaTimeoutSeconds());
        var error = tpa.request(from.getUniqueId(), to.getUniqueId(), kind, expires);
        if (error.isPresent()) {
            from.sendMessage(Component.text(error.get(), NamedTextColor.RED));
            return true;
        }
        String ask = kind == Kind.HERE
                ? from.getName() + " wants you to teleport to them."
                : from.getName() + " wants to teleport to you.";
        to.sendMessage(Component.text(ask + " /tpaccept or /tpdeny", NamedTextColor.GOLD));
        from.sendMessage(Component.text("Request sent to " + to.getName() + ".",
                NamedTextColor.GREEN));
        scheduler.runAsyncLater(() -> {
            tpa.incoming(to.getUniqueId()).ifPresent(pending -> {
                if (pending.from().equals(from.getUniqueId())) {
                    tpa.clear(pending);
                    Player stillFrom = Bukkit.getPlayer(from.getUniqueId());
                    if (stillFrom != null) {
                        scheduler.runForEntity(stillFrom, () -> stillFrom.sendMessage(
                                Component.text("Teleport request expired.", NamedTextColor.GRAY)),
                                null);
                    }
                }
            });
        }, Duration.ofSeconds(settings.tpaTimeoutSeconds()));
        return true;
    }

    private boolean accept(Player player) {
        var pending = tpa.incoming(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage(Component.text("No pending teleport request.", NamedTextColor.RED));
            return true;
        }
        Request request = pending.get();
        tpa.clear(request);
        Player from = Bukkit.getPlayer(request.from());
        if (from == null || !from.isOnline()) {
            player.sendMessage(Component.text("They went offline.", NamedTextColor.RED));
            return true;
        }
        Player moving = request.kind() == Kind.THERE ? from : player;
        Player destPlayer = request.kind() == Kind.THERE ? player : from;
        Location dest = destPlayer.getLocation();
        scheduler.runForEntity(moving, () -> moving.teleportAsync(dest).thenAccept(ok -> {
            Component done = Component.text("Teleport accepted.", NamedTextColor.GREEN);
            if (Boolean.TRUE.equals(ok)) {
                moving.sendMessage(done);
                destPlayer.sendMessage(done);
            } else {
                moving.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
            }
        }), null);
        return true;
    }

    private boolean deny(Player player) {
        var pending = tpa.incoming(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage(Component.text("No pending teleport request.", NamedTextColor.RED));
            return true;
        }
        Request request = pending.get();
        tpa.clear(request);
        player.sendMessage(Component.text("Teleport denied.", NamedTextColor.GRAY));
        Player from = Bukkit.getPlayer(request.from());
        if (from != null) {
            scheduler.runForEntity(from, () -> from.sendMessage(
                    Component.text(player.getName() + " denied the teleport.",
                            NamedTextColor.YELLOW)), null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ((name.equals("tpa") || name.equals("tpahere")) && args.length == 1) {
            return CommandTabs.onlinePlayers(sender, args[0]);
        }
        return List.of();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tpa.clearPlayer(event.getPlayer().getUniqueId());
    }
}
