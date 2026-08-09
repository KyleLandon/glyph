package com.glyph.core.economy.vault;

import com.glyph.core.GlyphCorePlugin;
import com.glyph.core.economy.EconomyRepository;
import com.glyph.core.player.PlayerRepository;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;

/**
 * Registers the {@link VaultEconomyBridge} with Bukkit's services manager.
 *
 * <p>Kept separate from {@link GlyphCorePlugin} on purpose: this class is the
 * only place (besides the bridge) that references Vault types, and it is only
 * loaded after the plugin manager confirms VaultUnlocked is installed. Without
 * it, GlyphCore runs normally with no Vault classes on the classpath.</p>
 */
public final class VaultBridgeRegistrar {

    private VaultBridgeRegistrar() {
    }

    public static void register(GlyphCorePlugin plugin,
                                EconomyRepository economyRepository,
                                PlayerRepository playerRepository) {
        VaultEconomyBridge bridge = new VaultEconomyBridge(
                economyRepository,
                playerRepository,
                plugin.economyService(),
                () -> plugin.databaseManager().isReady(),
                plugin.settings().economy().currencySymbol(),
                plugin.getSLF4JLogger());
        plugin.getServer().getServicesManager().register(
                Economy.class, bridge, plugin, ServicePriority.Highest);
        plugin.getSLF4JLogger().info(
                "Vault economy bridge registered (provider: {})", bridge.getName());
    }
}
