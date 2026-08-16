package com.glyph.core.smp.command;

import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /spawn} — teleport to this world's spawn. */
public final class SpawnCommand implements CommandExecutor {

    private final SchedulerAdapter scheduler;

    public SpawnCommand(SchedulerAdapter scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /spawn.", NamedTextColor.RED));
            return true;
        }
        World world = player.getWorld();
        Location spawn = world.getSpawnLocation().clone().add(0.5, 0, 0.5);
        scheduler.runForEntity(player, () -> player.teleportAsync(spawn).thenAccept(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                player.sendMessage(Component.text("Welcome to spawn.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
            }
        }), null);
        return true;
    }
}
