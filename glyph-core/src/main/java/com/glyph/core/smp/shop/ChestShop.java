package com.glyph.core.smp.shop;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public record ChestShop(
        UUID id,
        UUID ownerUuid,
        String market,
        String world,
        int x,
        int y,
        int z,
        Mode mode,
        long price,
        byte[] itemData) {

    public enum Mode { SELL, BUY }

    public Location location() {
        World loaded = Bukkit.getWorld(world);
        if (loaded == null) {
            return null;
        }
        return new Location(loaded, x, y, z);
    }

    public boolean isAt(Block block) {
        return block.getWorld().getName().equals(world)
                && block.getX() == x
                && block.getY() == y
                && block.getZ() == z;
    }
}
