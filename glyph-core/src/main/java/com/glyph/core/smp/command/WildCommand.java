package com.glyph.core.smp.command;

import com.glyph.core.claims.GriefPreventionAccess;
import com.glyph.core.config.SmpSettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /wild} — random unclaimed overworld teleport with a cooldown.
 */
public final class WildCommand implements CommandExecutor {

    private final SmpSettings settings;
    private final SchedulerAdapter scheduler;
    private final Map<UUID, Instant> cooldownUntil = new ConcurrentHashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();

    public WildCommand(SmpSettings settings, SchedulerAdapter scheduler) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /wild.", NamedTextColor.RED));
            return true;
        }
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(Component.text("/wild is overworld only. /spawn first.",
                    NamedTextColor.RED));
            return true;
        }
        Instant lockedUntil = cooldownUntil.get(player.getUniqueId());
        Instant now = Instant.now();
        if (lockedUntil != null && now.isBefore(lockedUntil)) {
            long seconds = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
            player.sendMessage(Component.text(
                    "Wilderness cooldown: " + seconds + "s", NamedTextColor.YELLOW));
            return true;
        }
        if (!searching.add(player.getUniqueId())) {
            player.sendMessage(Component.text("Already searching for wilderness.",
                    NamedTextColor.YELLOW));
            return true;
        }
        player.sendMessage(Component.text("Searching for wilderness...", NamedTextColor.GRAY));
        tryAttempt(player, 1);
        return true;
    }

    private void tryAttempt(Player player, int attempt) {
        if (attempt > settings.wildMaxAttempts()) {
            searching.remove(player.getUniqueId());
            scheduler.runForEntity(player, () -> player.sendMessage(Component.text(
                    "Could not find unclaimed land. Try again.", NamedTextColor.RED)), null);
            return;
        }
        World world = player.getWorld();
        Location spawn = world.getSpawnLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble() * Math.PI * 2;
        int dist = random.nextInt(settings.wildMinRadius(), settings.wildMaxRadius() + 1);
        int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!player.isOnline()) {
                searching.remove(player.getUniqueId());
                return;
            }
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!safe(ground)) {
                tryAttempt(player, attempt + 1);
                return;
            }
            Location dest = ground.getLocation().add(0.5, 1, 0.5);
            dest.setYaw(player.getLocation().getYaw());
            dest.setPitch(0);
            if (GriefPreventionAccess.isClaimed(dest)) {
                tryAttempt(player, attempt + 1);
                return;
            }
            scheduler.runForEntity(player, () -> player.teleportAsync(dest).thenAccept(ok -> {
                searching.remove(player.getUniqueId());
                if (Boolean.TRUE.equals(ok)) {
                    cooldownUntil.put(player.getUniqueId(),
                            Instant.now().plusSeconds(settings.wildCooldownSeconds()));
                    player.sendMessage(Component.text(
                            "Wilderness. /sethome if you want this spot.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
                }
            }), () -> searching.remove(player.getUniqueId()));
        }).exceptionally(error -> {
            searching.remove(player.getUniqueId());
            return null;
        });
    }

    private static boolean safe(Block ground) {
        Material type = ground.getType();
        if (!type.isSolid() || type.isAir()) {
            return false;
        }
        if (Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) {
            return false;
        }
        return switch (type) {
            case WATER, LAVA, MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE,
                    POWDER_SNOW, KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS,
                    BUBBLE_COLUMN, ICE, PACKED_ICE, BLUE_ICE -> false;
            default -> true;
        };
    }
}
