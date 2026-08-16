package com.glyph.core.smp.command;

import com.glyph.core.command.CommandTabs;
import com.glyph.core.smp.trade.TradeGui;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class TradeCommand implements CommandExecutor, TabCompleter {

    private final TradeGui trades;
    private final Map<UUID, UUID> pending = new ConcurrentHashMap<>();

    public TradeCommand(TradeGui trades) {
        this.trades = Objects.requireNonNull(trades, "trades");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can trade.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /trade <player>", NamedTextColor.RED));
            return true;
        }
        if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("yes")) {
            return accept(player);
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot trade with yourself.", NamedTextColor.RED));
            return true;
        }
        if (player.getLocation().distanceSquared(target.getLocation()) > 100) {
            player.sendMessage(Component.text("Get within 10 blocks to trade.", NamedTextColor.RED));
            return true;
        }
        if (trades.isBusy(player.getUniqueId()) || trades.isBusy(target.getUniqueId())) {
            player.sendMessage(Component.text("One of you is already in a trade.", NamedTextColor.RED));
            return true;
        }
        UUID waitingFor = pending.get(player.getUniqueId());
        if (target.getUniqueId().equals(waitingFor)) {
            pending.remove(player.getUniqueId());
            pending.remove(target.getUniqueId());
            trades.open(target, player);
            return true;
        }
        pending.put(target.getUniqueId(), player.getUniqueId());
        target.sendMessage(Component.text(
                player.getName() + " wants to trade. /trade " + player.getName() + " to accept.",
                NamedTextColor.GOLD));
        player.sendMessage(Component.text("Trade request sent to " + target.getName() + ".",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean accept(Player player) {
        UUID from = pending.remove(player.getUniqueId());
        if (from == null) {
            player.sendMessage(Component.text("No pending trade.", NamedTextColor.RED));
            return true;
        }
        Player other = Bukkit.getPlayer(from);
        if (other == null) {
            player.sendMessage(Component.text("They went offline.", NamedTextColor.RED));
            return true;
        }
        trades.open(other, player);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CommandTabs.onlinePlayers(sender, args[0]);
        }
        return List.of();
    }
}
