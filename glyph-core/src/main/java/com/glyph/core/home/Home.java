package com.glyph.core.home;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** One named bed-style home on a single backend market. */
public record Home(
        UUID playerUuid,
        String market,
        String name,
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
