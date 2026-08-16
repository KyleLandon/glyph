package com.glyph.discord;

import com.glyph.api.discord.DiscordTier;
import com.glyph.api.glyphs.GlyphTitle;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Environment-driven configuration for the Glyph Discord bot. */
public final class DiscordBotConfig {

    private final String token;
    private final long guildId;
    private final long verifiedRoleId;
    private final long alphaRoleId;
    private final Map<DiscordTier, Long> tierRoleIds;
    private final Map<GlyphTitle, Long> titleRoleIds;
    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    private DiscordBotConfig(
            String token,
            long guildId,
            long verifiedRoleId,
            long alphaRoleId,
            Map<DiscordTier, Long> tierRoleIds,
            Map<GlyphTitle, Long> titleRoleIds,
            String dbHost,
            int dbPort,
            String dbName,
            String dbUser,
            String dbPassword,
            String redisHost,
            int redisPort,
            String redisPassword) {
        this.token = token;
        this.guildId = guildId;
        this.verifiedRoleId = verifiedRoleId;
        this.alphaRoleId = alphaRoleId;
        this.tierRoleIds = Map.copyOf(tierRoleIds);
        this.titleRoleIds = Map.copyOf(titleRoleIds);
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
    }

    public static DiscordBotConfig fromEnv(MapLike env) {
        String token = require(env, "GLYPH_DISCORD_TOKEN");
        long guildId = requireLong(env, "GLYPH_DISCORD_GUILD_ID");
        // Role IDs optional — RoleBootstrap creates/finds them by name when unset.
        long verified = longOrZero(env, "GLYPH_DISCORD_ROLE_VERIFIED");
        long alpha = longOrZero(env, "GLYPH_DISCORD_ROLE_ALPHA");

        Map<DiscordTier, Long> tiers = new EnumMap<>(DiscordTier.class);
        for (DiscordTier tier : DiscordTier.values()) {
            String key = "GLYPH_DISCORD_ROLE_" + tier.name();
            long roleId = longOrZero(env, key);
            if (roleId != 0L) {
                tiers.put(tier, roleId);
            }
        }

        Map<GlyphTitle, Long> titles = new EnumMap<>(GlyphTitle.class);
        for (GlyphTitle title : GlyphTitle.values()) {
            String key = "GLYPH_DISCORD_ROLE_" + title.id().toUpperCase();
            long roleId = longOrZero(env, key);
            if (roleId != 0L) {
                titles.put(title, roleId);
            }
        }

        return new DiscordBotConfig(
                token,
                guildId,
                verified,
                alpha,
                tiers,
                titles,
                env.getOrDefault("GLYPH_DB_HOST", "localhost"),
                intOr(env, "GLYPH_DB_PORT", 5432),
                env.getOrDefault("GLYPH_DB_DATABASE", "glyph"),
                env.getOrDefault("GLYPH_DB_USERNAME", "glyph_app"),
                env.getOrDefault("GLYPH_DB_PASSWORD", ""),
                env.getOrDefault("GLYPH_REDIS_HOST", "localhost"),
                intOr(env, "GLYPH_REDIS_PORT", 6379),
                env.getOrDefault("GLYPH_REDIS_PASSWORD", ""));
    }

    public static DiscordBotConfig fromSystemEnv() {
        return fromEnv(systemEnv());
    }

    public static DiscordBotConfig load() {
        try {
            List<Path> candidates = new java.util.ArrayList<>();
            Path fromEnv = pathOrNull(System.getenv("GLYPH_DISCORD_ENV_FILE"));
            if (fromEnv != null) {
                candidates.add(fromEnv);
            }
            candidates.add(Path.of("secrets.env"));
            candidates.add(Path.of("glyph-discord", "secrets.env"));
            candidates.add(Path.of(
                    "glyph-core", "src", "main", "java", "com", "glyph", "core", "discord",
                    "secrets.env"));
            Map<String, String> file = EnvFile.loadFirstExisting(candidates);
            return fromEnv(EnvFile.merge(file, System.getenv()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load Discord bot config", e);
        }
    }

    public DiscordBotConfig withRoles(
            long verifiedRoleId,
            long alphaRoleId,
            Map<DiscordTier, Long> tierRoleIds,
            Map<GlyphTitle, Long> titleRoleIds) {
        return new DiscordBotConfig(
                token,
                guildId,
                verifiedRoleId,
                alphaRoleId,
                tierRoleIds,
                titleRoleIds,
                dbHost,
                dbPort,
                dbName,
                dbUser,
                dbPassword,
                redisHost,
                redisPort,
                redisPassword);
    }

    private static MapLike systemEnv() {
        return new MapLike() {
            @Override
            public String get(String key) {
                return System.getenv(key);
            }

            @Override
            public String getOrDefault(String key, String def) {
                String value = System.getenv(key);
                return (value == null || value.isBlank()) ? def : value;
            }
        };
    }

    private static Path pathOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value.trim());
    }

    public String token() {
        return token;
    }

    public long guildId() {
        return guildId;
    }

    public long verifiedRoleId() {
        return verifiedRoleId;
    }

    public long alphaRoleId() {
        return alphaRoleId;
    }

    public boolean hasAlphaRole() {
        return alphaRoleId != 0L;
    }

    public Map<DiscordTier, Long> tierRoleIds() {
        return tierRoleIds;
    }

    public Map<GlyphTitle, Long> titleRoleIds() {
        return titleRoleIds;
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    public String redisHost() {
        return redisHost;
    }

    public int redisPort() {
        return redisPort;
    }

    public String redisPassword() {
        return redisPassword;
    }

    public boolean hasRedisPassword() {
        return redisPassword != null && !redisPassword.isBlank();
    }

    private static String require(MapLike env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value.trim();
    }

    private static long requireLong(MapLike env, String key) {
        String value = require(env, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be a snowflake/long: " + value, e);
        }
    }

    private static long longOrZero(MapLike env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be a snowflake/long: " + value, e);
        }
    }

    private static int intOr(MapLike env, String key, int def) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be an integer: " + value, e);
        }
    }

    public interface MapLike {
        String get(String key);

        String getOrDefault(String key, String def);
    }
}
