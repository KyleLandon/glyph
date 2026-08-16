package com.glyph.core.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Builds {@link GlyphSettings} from YAML configuration sections, applying
 * {@code GLYPH_*} environment variable overrides.
 *
 * <p>Precedence: environment variable &gt; YAML value &gt; built-in default.
 * The environment map is injected so tests do not depend on the real process
 * environment.</p>
 */
public final class SettingsLoader {

    private final Map<String, String> env;

    public SettingsLoader(Map<String, String> env) {
        this.env = Objects.requireNonNull(env, "env");
    }

    public GlyphSettings load(
            ConfigurationSection config,
            ConfigurationSection database,
            ConfigurationSection redis) {

        String serverId = str(config, "server.id", "GLYPH_SERVER_ID", "glyph-01");
        ServerRole role = ServerRole.from(str(config, "server.role", "GLYPH_SERVER_ROLE", "anarchy"));

        DatabaseSettings databaseSettings = new DatabaseSettings(
                str(database, "database.host", "GLYPH_DB_HOST", "localhost"),
                intVal(database, "database.port", "GLYPH_DB_PORT", 5432),
                str(database, "database.name", "GLYPH_DB_DATABASE", "glyph"),
                str(database, "database.username", "GLYPH_DB_USERNAME", "glyph_app"),
                str(database, "database.password", "GLYPH_DB_PASSWORD", ""),
                intVal(database, "database.pool.minimum-idle", null, 2),
                intVal(database, "database.pool.maximum-pool-size", null, 10),
                longVal(database, "database.pool.connection-timeout-ms", null, 5_000L),
                longVal(database, "database.pool.idle-timeout-ms", null, 600_000L),
                longVal(database, "database.pool.max-lifetime-ms", null, 1_800_000L));

        RedisSettings redisSettings = new RedisSettings(
                str(redis, "redis.host", "GLYPH_REDIS_HOST", "localhost"),
                intVal(redis, "redis.port", "GLYPH_REDIS_PORT", 6379),
                str(redis, "redis.password", "GLYPH_REDIS_PASSWORD", ""));

        EconomySettings economySettings = new EconomySettings(
                startingBalance(config),
                str(config, "economy.currency-symbol", null, "$"),
                boolVal(config, "economy.hud.enabled", true),
                str(config, "economy.hud.title", null, "GLYPH"));

        TabSettings tabSettings = new TabSettings(
                boolVal(config, "tab.enabled", true),
                str(config, "tab.header", null, "GLYPH"),
                str(config, "tab.footer", null, "play.glyphmc.net"));

        // Percent values from YAML become basis points so all fee math stays
        // in integer whole dollars (GDD section 63).
        AuctionSettings auctionSettings = new AuctionSettings(
                boolVal(config, "auction.enabled", true),
                basisPoints(config, "auction.listing-fee-percent", 1.0),
                basisPoints(config, "auction.sale-fee-percent", 5.0),
                intVal(config, "auction.max-listings-per-player", null, 10),
                intVal(config, "auction.duration-hours", null, 48));

        BountySettings bountySettings = new BountySettings(
                boolVal(config, "bounties.enabled", true),
                longVal(config, "bounties.minimum", null, 100L),
                intVal(config, "bounties.same-victim-cooldown-minutes", null, 60));

        PlaytimeRewardSettings rewardSettings = new PlaytimeRewardSettings(
                boolVal(config, "rewards.playtime.enabled", true),
                intVal(config, "rewards.playtime.interval-minutes", null, 15),
                longVal(config, "rewards.playtime.amount", null, 10L),
                intVal(config, "rewards.playtime.min-activity", null, 20));

        GlyphCurrencySettings glyphSettings = new GlyphCurrencySettings(
                boolVal(config, "glyphs.enabled", true),
                glyphSymbol(config),
                longVal(config, "glyphs.first-bounty-reward", null, 3L));

        DiscordSettings discordSettings = new DiscordSettings(
                str(config, "discord.invite-url", "GLYPH_DISCORD_INVITE_URL",
                        "https://discord.gg/htkQHR4gdf"));

        ChatSettings chatSettings = new ChatSettings(
                boolVal(config, "chat.item-placeholders", true),
                boolVal(config, "chat.local-enabled", true),
                intVal(config, "chat.local-radius", null, 100));

        StarterSettings starterSettings = new StarterSettings(
                boolVal(config, "starter.enabled", true),
                starterItems(config));

        SmpSettings smpSettings = new SmpSettings(
                intVal(config, "smp.wild.min-radius", null, 500),
                intVal(config, "smp.wild.max-radius", null, 15_000),
                intVal(config, "smp.wild.cooldown-seconds", null, 300),
                intVal(config, "smp.wild.max-attempts", null, 24),
                intVal(config, "smp.tpa.timeout-seconds", null, 60),
                boolVal(config, "smp.sleep.one-player", true),
                longVal(config, "smp.warps.create-cost", null, 250L),
                intVal(config, "smp.warps.max-per-player", null, 3),
                longVal(config, "smp.claim-blocks.pack-price", null, 50L),
                intVal(config, "smp.claim-blocks.pack-size", null, 100),
                boolVal(config, "smp.sit", true),
                boolVal(config, "smp.shops", true),
                boolVal(config, "smp.trade", true),
                boolVal(config, "smp.image-maps", true));

        return new GlyphSettings(
                serverId, role, databaseSettings, redisSettings, economySettings, tabSettings,
                auctionSettings, bountySettings, rewardSettings, glyphSettings, discordSettings,
                chatSettings, starterSettings, smpSettings);
    }

    private List<StarterSettings.StarterItem> starterItems(ConfigurationSection config) {
        List<String> raw = config != null ? config.getStringList("starter.items") : List.of();
        if (raw.isEmpty()) {
            return StarterSettings.defaults();
        }
        return raw.stream().map(StarterSettings::parseItem).toList();
    }

    /**
     * Whole-dollar first-join grant. Prefers {@code economy.starting-balance};
     * falls back to legacy {@code economy.starting-balance-minor} (cents / 100)
     * for configs written before ADR-010; default matches packaged config.yml.
     */
    private long startingBalance(ConfigurationSection config) {
        if (config != null && config.isSet("economy.starting-balance")) {
            return Math.max(0L, config.getLong("economy.starting-balance"));
        }
        if (config != null && config.isSet("economy.starting-balance-minor")) {
            long minor = Math.max(0L, config.getLong("economy.starting-balance-minor"));
            return minor / 100L;
        }
        return 100L;
    }

    private int basisPoints(ConfigurationSection section, String path, double defPercent) {
        double percent = section != null ? section.getDouble(path, defPercent) : defPercent;
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException(path + " must be between 0 and 100: " + percent);
        }
        return (int) Math.round(percent * 100);
    }

    /** Glyph mark: ✦ (U+2726). Rejects mojibake/`?` from broken Windows saves. */
    private String glyphSymbol(ConfigurationSection config) {
        String fromEnv = envValue("GLYPH_GLYPHS_SYMBOL");
        if (fromEnv != null) {
            return fromEnv;
        }
        String raw = config != null ? config.getString("glyphs.symbol", "✦") : "✦";
        if (raw == null || raw.isBlank() || "?".equals(raw) || "�".equals(raw)) {
            return "✦";
        }
        return raw;
    }

    private String str(ConfigurationSection section, String path, String envKey, String def) {
        String fromEnv = envValue(envKey);
        if (fromEnv != null) {
            return fromEnv;
        }
        return section != null ? section.getString(path, def) : def;
    }

    private int intVal(ConfigurationSection section, String path, String envKey, int def) {
        String fromEnv = envValue(envKey);
        if (fromEnv != null) {
            try {
                return Integer.parseInt(fromEnv.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Environment variable " + envKey + " is not a valid integer: " + fromEnv);
            }
        }
        return section != null ? section.getInt(path, def) : def;
    }

    private long longVal(ConfigurationSection section, String path, String envKey, long def) {
        String fromEnv = envValue(envKey);
        if (fromEnv != null) {
            try {
                return Long.parseLong(fromEnv.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Environment variable " + envKey + " is not a valid long: " + fromEnv);
            }
        }
        return section != null ? section.getLong(path, def) : def;
    }

    private boolean boolVal(ConfigurationSection section, String path, boolean def) {
        return section != null ? section.getBoolean(path, def) : def;
    }

    private String envValue(String key) {
        if (key == null) {
            return null;
        }
        String value = env.get(key);
        return (value == null || value.isBlank()) ? null : value;
    }
}
