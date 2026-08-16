package com.glyph.core.player.command;

import com.glyph.core.economy.command.CommandFeedback;
import com.glyph.core.player.StarterKitService;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /starter} — claim the stone-tool pack. {@code /starter <player>}
 * force-gives to an online player (staff).
 */
public final class StarterCommand implements CommandExecutor, TabCompleter {

    public static final String ADMIN_PERMISSION = "glyph.starter.admin";

    private final StarterKitService starterKit;
    private final SchedulerAdapter scheduler;

    public StarterCommand(StarterKitService starterKit, SchedulerAdapter scheduler) {
        this.starterKit = starterKit;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!starterKit.enabled()) {
            sender.sendMessage(Component.text("Starter packs are disabled.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            claimSelf(sender);
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("Usage: /" + label, NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(Component.text("Player must be online: " + args[0], NamedTextColor.RED));
            return true;
        }
        scheduler.runForEntity(target, () -> {
            starterKit.grant(target);
            CommandFeedback.deliver(scheduler, sender, Component.text(
                    "Gave the starter pack to " + target.getName() + ".", NamedTextColor.GREEN));
        }, () -> CommandFeedback.deliver(scheduler, sender, Component.text(
                target.getName() + " left before the pack could be given.", NamedTextColor.RED)));
        return true;
    }

    private void claimSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Usage: /starter <player>", NamedTextColor.RED));
            return;
        }
        scheduler.runForEntity(player, () -> {
            if (starterKit.alreadyGranted(player)) {
                player.sendMessage(Component.text(
                        "You already received the starter pack.", NamedTextColor.RED));
                return;
            }
            starterKit.grant(player);
        }, null);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
