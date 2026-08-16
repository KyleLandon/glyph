package com.glyph.core.smp.armorstand;

import com.glyph.core.claims.GriefPreventionAccess;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.EulerAngle;

/** Sneak + right-click an armor stand you can build at to edit pose and flags. */
public final class ArmorStandEditor implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof ArmorStand stand)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        if (GriefPreventionAccess.present()
                && !GriefPreventionAccess.canBuild(player, stand.getLocation())) {
            return;
        }
        event.setCancelled(true);
        open(player, stand);
    }

    private void open(Player player, ArmorStand stand) {
        Holder holder = new Holder(stand);
        Inventory inventory = Bukkit.createInventory(holder, 9, Component.text("Armor Stand"));
        holder.inventory = inventory;
        paint(holder);
        player.openInventory(inventory);
    }

    private static void paint(Holder holder) {
        ArmorStand stand = holder.stand;
        holder.inventory.setItem(0, toggle(Material.ARMOR_STAND, "Small", stand.isSmall()));
        holder.inventory.setItem(1, toggle(Material.STICK, "Arms", stand.hasArms()));
        holder.inventory.setItem(2, toggle(Material.SMOOTH_STONE_SLAB, "Base plate", stand.hasBasePlate()));
        holder.inventory.setItem(3, toggle(Material.SAND, "Gravity", stand.hasGravity()));
        holder.inventory.setItem(4, toggle(Material.GLOWSTONE_DUST, "Glow", stand.isGlowing()));
        holder.inventory.setItem(5, toggle(Material.NAME_TAG, "Name visible", stand.isCustomNameVisible()));
        holder.inventory.setItem(6, named(Material.ARROW, "Rotate left 15°"));
        holder.inventory.setItem(7, named(Material.SPECTRAL_ARROW, "Rotate right 15°"));
        holder.inventory.setItem(8, named(Material.PLAYER_HEAD, "Reset pose"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!holder.stand.isValid()) {
            event.getWhoClicked().closeInventory();
            return;
        }
        ArmorStand stand = holder.stand;
        switch (event.getRawSlot()) {
            case 0 -> stand.setSmall(!stand.isSmall());
            case 1 -> stand.setArms(!stand.hasArms());
            case 2 -> stand.setBasePlate(!stand.hasBasePlate());
            case 3 -> stand.setGravity(!stand.hasGravity());
            case 4 -> stand.setGlowing(!stand.isGlowing());
            case 5 -> stand.setCustomNameVisible(!stand.isCustomNameVisible());
            case 6 -> {
                Location loc = stand.getLocation();
                loc.setYaw(loc.getYaw() - 15);
                stand.teleport(loc);
            }
            case 7 -> {
                Location loc = stand.getLocation();
                loc.setYaw(loc.getYaw() + 15);
                stand.teleport(loc);
            }
            case 8 -> {
                stand.setHeadPose(EulerAngle.ZERO);
                stand.setBodyPose(EulerAngle.ZERO);
                stand.setLeftArmPose(EulerAngle.ZERO);
                stand.setRightArmPose(EulerAngle.ZERO);
                stand.setLeftLegPose(EulerAngle.ZERO);
                stand.setRightLegPose(EulerAngle.ZERO);
            }
            default -> {
                return;
            }
        }
        paint(holder);
    }

    private static ItemStack toggle(Material material, String label, boolean on) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label + (on ? " (on)" : " (off)"),
                        on ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Click to toggle", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements InventoryHolder {
        private final ArmorStand stand;
        private Inventory inventory;

        private Holder(ArmorStand stand) {
            this.stand = stand;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
