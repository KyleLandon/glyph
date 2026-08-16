package com.glyph.core.claims;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Reflection bridge to GriefPrevention so GlyphCore compiles without the jar
 * on the Gradle classpath. All methods no-op when GP is absent.
 */
public final class GriefPreventionAccess {

    private GriefPreventionAccess() {
    }

    public static boolean present() {
        return Bukkit.getPluginManager().getPlugin("GriefPrevention") != null;
    }

    /** {@code true} when a Survival claim covers this block. */
    public static boolean isClaimed(Location location) {
        return claimAt(location).isPresent();
    }

    public static Optional<UUID> claimOwner(Location location) {
        Object claim = claimAt(location).orElse(null);
        if (claim == null) {
            return Optional.empty();
        }
        try {
            Object owner = claim.getClass().getField("ownerID").get(claim);
            return owner instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
        } catch (ReflectiveOperationException e) {
            log("claim owner", e);
            return Optional.empty();
        }
    }

    /** Build trust (includes the owner). Admin claims: GP's allowBuild. */
    public static boolean canBuild(Player player, Location location) {
        if (!present()) {
            return true;
        }
        Object claim = claimAt(location).orElse(null);
        if (claim == null) {
            return true;
        }
        try {
            Method allowBuild = claim.getClass().getMethod("allowBuild", Player.class, Material.class);
            Object denial = allowBuild.invoke(claim, player, Material.CHEST);
            return denial == null;
        } catch (ReflectiveOperationException e) {
            log("allowBuild", e);
            return false;
        }
    }

    public static Optional<Integer> remainingClaimBlocks(UUID playerUuid) {
        Object data = playerData(playerUuid).orElse(null);
        if (data == null) {
            return Optional.empty();
        }
        try {
            Method remaining = data.getClass().getMethod("getRemainingClaimBlocks");
            Object value = remaining.invoke(data);
            return value instanceof Integer n ? Optional.of(n) : Optional.empty();
        } catch (ReflectiveOperationException e) {
            log("remaining blocks", e);
            return Optional.empty();
        }
    }

    /**
     * Adds bonus claim blocks and persists. Returns the new remaining total,
     * or empty if GP is missing / the call failed.
     */
    public static Optional<Integer> addBonusClaimBlocks(UUID playerUuid, int amount) {
        if (amount <= 0) {
            return remainingClaimBlocks(playerUuid);
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        Object data = playerData(playerUuid).orElse(null);
        if (plugin == null || data == null) {
            return Optional.empty();
        }
        try {
            Method getBonus = data.getClass().getMethod("getBonusClaimBlocks");
            Method setBonus = data.getClass().getMethod("setBonusClaimBlocks", int.class);
            int current = (Integer) getBonus.invoke(data);
            setBonus.invoke(data, Math.addExact(current, amount));
            Object store = plugin.getClass().getField("dataStore").get(plugin);
            Method save = store.getClass().getMethod("savePlayerData", UUID.class, data.getClass());
            save.invoke(store, playerUuid, data);
            return remainingClaimBlocks(playerUuid);
        } catch (ReflectiveOperationException | ArithmeticException e) {
            log("bonus claim blocks", e);
            return Optional.empty();
        }
    }

    /**
     * Admin claim covering a cuboid. Owner {@code null} in GP means
     * administrative. Returns false when GP is absent or the call fails.
     */
    public static boolean createAdminClaim(
            Location corner1, Location corner2) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (plugin == null || corner1.getWorld() == null) {
            return false;
        }
        try {
            Object store = plugin.getClass().getField("dataStore").get(plugin);
            Method create = store.getClass().getMethod(
                    "createClaim",
                    org.bukkit.World.class,
                    int.class, int.class, int.class, int.class, int.class, int.class,
                    UUID.class, Class.forName("me.ryanhamshire.GriefPrevention.Claim"),
                    Integer.class, Player.class);
            int x1 = Math.min(corner1.getBlockX(), corner2.getBlockX());
            int x2 = Math.max(corner1.getBlockX(), corner2.getBlockX());
            int y1 = Math.min(corner1.getBlockY(), corner2.getBlockY());
            int y2 = Math.max(corner1.getBlockY(), corner2.getBlockY());
            int z1 = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
            int z2 = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
            Object result = create.invoke(
                    store, corner1.getWorld(), x1, x2, y1, y2, z1, z2,
                    null, null, null, null);
            return result != null;
        } catch (ReflectiveOperationException e) {
            log("create admin claim", e);
            return false;
        }
    }

    private static Optional<Object> claimAt(Location location) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (plugin == null) {
            return Optional.empty();
        }
        try {
            Object store = plugin.getClass().getField("dataStore").get(plugin);
            Method getClaimAt = store.getClass().getMethod(
                    "getClaimAt", Location.class, boolean.class,
                    Class.forName("me.ryanhamshire.GriefPrevention.Claim"));
            Object claim = getClaimAt.invoke(store, location, false, null);
            return Optional.ofNullable(claim);
        } catch (ReflectiveOperationException e) {
            log("getClaimAt", e);
            return Optional.empty();
        }
    }

    private static Optional<Object> playerData(UUID playerUuid) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (plugin == null) {
            return Optional.empty();
        }
        try {
            Object store = plugin.getClass().getField("dataStore").get(plugin);
            Method getPlayerData = store.getClass().getMethod("getPlayerData", UUID.class);
            return Optional.ofNullable(getPlayerData.invoke(store, playerUuid));
        } catch (ReflectiveOperationException e) {
            log("getPlayerData", e);
            return Optional.empty();
        }
    }

    private static void log(String what, Exception e) {
        Bukkit.getLogger().log(Level.WARNING, "GriefPrevention access failed: " + what, e);
    }
}
