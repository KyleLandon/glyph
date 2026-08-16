package com.glyph.core.smp.warp;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record PlayerWarp(
        String name,
        UUID ownerUuid,
        String market,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch) {

    public Location toLocation() {
        World loaded = Bukkit.getWorld(world);
        if (loaded == null) {
            return null;
        }
        return new Location(loaded, x, y, z, yaw, pitch);
    }
}
