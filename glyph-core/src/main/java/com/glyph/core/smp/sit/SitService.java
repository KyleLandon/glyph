package com.glyph.core.smp.sit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** {@code /sit} plus right-click stairs/slabs. Sneak to stand. */
public final class SitService implements Listener {

    private final Map<UUID, ArmorStand> seats = new ConcurrentHashMap<>();

    public boolean sit(Player player) {
        return sitAt(player, player.getLocation());
    }

    public boolean sitAt(Player player, Location location) {
        if (seats.containsKey(player.getUniqueId())) {
            stand(player);
            return true;
        }
        Location seat = location.clone();
        seat.setYaw(player.getLocation().getYaw());
        seat.setPitch(0);
        ArmorStand stand = player.getWorld().spawn(seat, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setBasePlate(false);
            as.setArms(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setCanPickupItems(false);
            as.setVelocity(new Vector());
        });
        if (!stand.addPassenger(player)) {
            stand.remove();
            player.sendMessage(Component.text("Cannot sit here.", NamedTextColor.RED));
            return false;
        }
        seats.put(player.getUniqueId(), stand);
        player.sendMessage(Component.text("Sitting. Sneak to stand.", NamedTextColor.GRAY));
        return true;
    }

    public void stand(Player player) {
        ArmorStand stand = seats.remove(player.getUniqueId());
        if (stand != null && stand.isValid()) {
            stand.removePassenger(player);
            stand.remove();
        }
    }

    public void standAll() {
        for (ArmorStand stand : seats.values()) {
            if (stand.isValid()) {
                stand.remove();
            }
        }
        seats.clear();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getPlayer().isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !sittable(block)) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (!hand.getType().isAir() && hand.getType().isBlock()) {
            return;
        }
        event.setCancelled(true);
        Location dest = block.getLocation().add(0.5, sitOffset(block), 0.5);
        dest.setYaw(event.getPlayer().getLocation().getYaw());
        sitAt(event.getPlayer(), dest);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && seats.containsKey(event.getPlayer().getUniqueId())) {
            stand(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stand(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stand(event.getPlayer());
    }

    private static boolean sittable(Block block) {
        if (Tag.STAIRS.isTagged(block.getType()) && block.getBlockData() instanceof Stairs stairs) {
            return stairs.getHalf() == Bisected.Half.BOTTOM;
        }
        if (Tag.SLABS.isTagged(block.getType()) && block.getBlockData() instanceof Slab slab) {
            return slab.getType() != Slab.Type.DOUBLE;
        }
        return false;
    }

    private static double sitOffset(Block block) {
        if (Tag.SLABS.isTagged(block.getType()) && block.getBlockData() instanceof Slab slab
                && slab.getType() == Slab.Type.BOTTOM) {
            return 0.5;
        }
        return 0.3;
    }
}
