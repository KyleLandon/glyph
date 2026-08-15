package com.glyph.core.player;

import com.glyph.core.config.StarterSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Grants the configured starter pack + rules book once per playerdata.
 *
 * <p>Keyed off persistent player data (not the database first-join flag) so a
 * world wipe re-issues tools after inventories are deleted, without minting
 * starter cash a second time.</p>
 */
public final class StarterKitService {

    private final StarterSettings settings;
    private final NamespacedKey grantedKey;

    public StarterKitService(Plugin plugin, StarterSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.grantedKey = new NamespacedKey(
                Objects.requireNonNull(plugin, "plugin"), "starter_kit");
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public boolean alreadyGranted(Player player) {
        return Boolean.TRUE.equals(player.getPersistentDataContainer()
                .get(grantedKey, PersistentDataType.BOOLEAN));
    }

    /**
     * One-time grant. {@code firstJoin} skips the chat line (welcome copy
     * covers it). Must run on the player's entity thread.
     *
     * @return {@code true} if items were added this call
     */
    public boolean grantIfNeeded(Player player, boolean firstJoin) {
        if (!settings.enabled() || alreadyGranted(player)) {
            return false;
        }
        give(player);
        if (!firstJoin) {
            player.sendMessage(Component.text(
                    "Starter tools and a rules book are in your inventory. /rules to read.",
                    NamedTextColor.GOLD));
        }
        return true;
    }

    /**
     * Always gives the pack and marks it granted. Used by {@code /starter}
     * (self, if not yet granted) and staff {@code /starter <player>}.
     */
    public void grant(Player player) {
        give(player);
        player.sendMessage(Component.text(
                "Starter tools and a rules book are in your inventory. /rules to read.",
                NamedTextColor.GOLD));
    }

    private void give(Player player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (StarterSettings.StarterItem item : settings.items()) {
            stacks.add(item.stack());
        }
        stacks.add(RulesBook.create());

        Map<Integer, ItemStack> leftover = player.getInventory()
                .addItem(stacks.toArray(ItemStack[]::new));
        leftover.values().forEach(dropped ->
                player.getWorld().dropItemNaturally(player.getLocation(), dropped));

        player.getPersistentDataContainer().set(grantedKey, PersistentDataType.BOOLEAN, true);
    }
}
