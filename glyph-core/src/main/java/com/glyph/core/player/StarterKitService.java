package com.glyph.core.player;

import com.glyph.core.config.ServerRole;
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
    private final ServerRole role;
    private final NamespacedKey grantedKey;

    public StarterKitService(Plugin plugin, StarterSettings settings, ServerRole role) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.role = role == null ? ServerRole.ANARCHY : role;
        this.grantedKey = new NamespacedKey(
                Objects.requireNonNull(plugin, "plugin"), "starter_kit");
    }

    /**
     * @return {@code true} if items were added this call
     */
    public boolean grantIfNeeded(Player player, boolean firstJoin) {
        if (!settings.enabled()) {
            return false;
        }
        if (Boolean.TRUE.equals(player.getPersistentDataContainer()
                .get(grantedKey, PersistentDataType.BOOLEAN))) {
            return false;
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (StarterSettings.StarterItem item : settings.items()) {
            stacks.add(item.stack());
        }
        stacks.add(role.isSmp() ? RulesBook.createSmp() : RulesBook.create());

        Map<Integer, ItemStack> leftover = player.getInventory()
                .addItem(stacks.toArray(ItemStack[]::new));
        leftover.values().forEach(dropped ->
                player.getWorld().dropItemNaturally(player.getLocation(), dropped));

        player.getPersistentDataContainer().set(grantedKey, PersistentDataType.BOOLEAN, true);

        if (!firstJoin) {
            player.sendMessage(Component.text(
                    "Starter tools and a rules book are in your inventory. /rules to read.",
                    NamedTextColor.GOLD));
        }
        return true;
    }
}
