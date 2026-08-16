package com.glyph.core.smp.market;

import com.glyph.core.claims.GriefPreventionAccess;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

/**
 * Builds a 5-stall cobble street south of world spawn and tries to wrap it
 * in a GriefPrevention admin claim.
 */
public final class MarketBuilder {

    private static final int STALLS = 5;
    private static final int STALL_WIDTH = 7;
    private static final int GAP = 2;

    private MarketBuilder() {
    }

    public static void build(Player actor) {
        World world = actor.getWorld();
        Location spawn = world.getSpawnLocation();
        int originX = spawn.getBlockX() - ((STALLS * (STALL_WIDTH + GAP) - GAP) / 2);
        int originZ = spawn.getBlockZ() + 12;
        int groundY = spawn.getBlockY() - 1;

        int minX = originX - 2;
        int maxX = originX + STALLS * (STALL_WIDTH + GAP) + 2;
        int minZ = originZ - 4;
        int maxZ = originZ + 12;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= originZ + 1; z++) {
                world.getBlockAt(x, groundY, z).setType(Material.COBBLESTONE, false);
            }
        }

        for (int i = 0; i < STALLS; i++) {
            int x0 = originX + i * (STALL_WIDTH + GAP);
            buildStall(world, x0, groundY, originZ + 2, i + 1);
        }

        Location c1 = new Location(world, minX, world.getMinHeight(), minZ);
        Location c2 = new Location(world, maxX, world.getMaxHeight(), maxZ);
        boolean claimed = GriefPreventionAccess.createAdminClaim(c1, c2);
        actor.sendMessage(Component.text(
                claimed
                        ? "Market street built south of spawn (admin claim)."
                        : "Market street built south of spawn. Claim it with a golden shovel if needed.",
                NamedTextColor.GREEN));
        actor.sendMessage(Component.text(
                "Staff: /trust players on a stall, they /shop sell on the chest.",
                NamedTextColor.GRAY));
    }

    private static void buildStall(World world, int x0, int groundY, int z0, int number) {
        int x1 = x0 + STALL_WIDTH - 1;
        int z1 = z0 + 6;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                world.getBlockAt(x, groundY, z).setType(Material.STRIPPED_OAK_LOG, false);
                world.getBlockAt(x, groundY + 3, z).setType(Material.OAK_SLAB, false);
            }
        }
        for (int y = groundY + 1; y <= groundY + 2; y++) {
            world.getBlockAt(x0, y, z0).setType(Material.OAK_LOG, false);
            world.getBlockAt(x1, y, z0).setType(Material.OAK_LOG, false);
            world.getBlockAt(x0, y, z1).setType(Material.OAK_LOG, false);
            world.getBlockAt(x1, y, z1).setType(Material.OAK_LOG, false);
        }
        int chestX = x0 + STALL_WIDTH / 2;
        int chestZ = z0;
        Block chestBlock = world.getBlockAt(chestX, groundY + 1, chestZ);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getBlockData() instanceof Directional directional) {
            directional.setFacing(BlockFace.NORTH);
            chestBlock.setBlockData(directional, false);
        }
        if (chestBlock.getState() instanceof Chest chest) {
            chest.customName(Component.text("Stall " + number));
            chest.update();
        }
        Block signBlock = world.getBlockAt(chestX, groundY + 1, chestZ - 1);
        signBlock.setType(Material.OAK_WALL_SIGN, false);
        if (signBlock.getBlockData() instanceof Directional directional) {
            directional.setFacing(BlockFace.NORTH);
            signBlock.setBlockData(directional, false);
        }
        if (signBlock.getState() instanceof Sign sign) {
            var side = sign.getSide(Side.FRONT);
            side.line(0, Component.text("STALL " + number, NamedTextColor.DARK_BLUE));
            side.line(1, Component.text("Ask staff", NamedTextColor.BLACK));
            side.line(2, Component.text("then /shop sell", NamedTextColor.DARK_GRAY));
            sign.update();
        }
    }
}
