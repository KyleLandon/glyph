package com.glyph.core.config;

import java.io.File;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * All GlyphCore settings, loaded once during plugin enable.
 */
public record GlyphSettings(
        String serverId,
        DatabaseSettings database,
        RedisSettings redis,
        EconomySettings economy,
        TabSettings tab,
        AuctionSettings auction,
        BountySettings bounties,
        PlaytimeRewardSettings rewards,
        StarterSettings starter) {

    /**
     * Saves default config files if missing, then loads them applying
     * environment variable overrides.
     */
    public static GlyphSettings load(JavaPlugin plugin) {
        saveDefault(plugin, "config.yml");
        saveDefault(plugin, "database.yml");
        saveDefault(plugin, "redis.yml");

        YamlConfiguration config = loadYaml(plugin, "config.yml");
        YamlConfiguration database = loadYaml(plugin, "database.yml");
        YamlConfiguration redis = loadYaml(plugin, "redis.yml");

        return new SettingsLoader(System.getenv()).load(config, database, redis);
    }

    private static void saveDefault(JavaPlugin plugin, String name) {
        if (!new File(plugin.getDataFolder(), name).exists()) {
            plugin.saveResource(name, false);
        }
    }

    private static YamlConfiguration loadYaml(JavaPlugin plugin, String name) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    /** Copy of this settings object safe to log (no credentials). */
    public Map<String, String> describe() {
        return Map.of(
                "server.id", serverId,
                "database", database.host() + ":" + database.port() + "/" + database.database(),
                "redis", redis.host() + ":" + redis.port());
    }
}
