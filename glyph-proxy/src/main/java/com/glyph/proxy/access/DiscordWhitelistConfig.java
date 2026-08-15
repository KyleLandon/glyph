package com.glyph.proxy.access;

/**
 * Proxy-side Discord whitelist settings (env-driven).
 *
 * <p>Default off so public joins keep working until alpha is enabled.</p>
 */
public final class DiscordWhitelistConfig {

    private final boolean enabled;
    private final String inviteUrl;
    private final String jdbcUrl;
    private final String dbUser;
    private final String dbPassword;

    public DiscordWhitelistConfig(
            boolean enabled,
            String inviteUrl,
            String jdbcUrl,
            String dbUser,
            String dbPassword) {
        this.enabled = enabled;
        this.inviteUrl = inviteUrl;
        this.jdbcUrl = jdbcUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static DiscordWhitelistConfig fromEnv() {
        boolean enabled = Boolean.parseBoolean(
                envOr("GLYPH_DISCORD_WHITELIST", "false"));
        String host = envOr("GLYPH_DB_HOST", "localhost");
        int port = Integer.parseInt(envOr("GLYPH_DB_PORT", "5432"));
        String database = envOr("GLYPH_DB_DATABASE", "glyph");
        String user = envOr("GLYPH_DB_USERNAME", "glyph_app");
        String password = envOr("GLYPH_DB_PASSWORD", "");
        String invite = envOr("GLYPH_DISCORD_INVITE_URL", "https://discord.gg/htkQHR4gdf");
        return new DiscordWhitelistConfig(
                enabled,
                invite,
                "jdbc:postgresql://" + host + ":" + port + "/" + database,
                user,
                password);
    }

    public boolean enabled() {
        return enabled;
    }

    public String inviteUrl() {
        return inviteUrl;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String dbUser() {
        return dbUser;
    }

    public String dbPassword() {
        return dbPassword;
    }

    private static String envOr(String key, String def) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? def : value.trim();
    }
}
