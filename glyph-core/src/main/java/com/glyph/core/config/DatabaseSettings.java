package com.glyph.core.config;

/**
 * PostgreSQL connection and pool settings. Values come from
 * {@code database.yml} with {@code GLYPH_DB_*} environment overrides.
 */
public record DatabaseSettings(
        String host,
        int port,
        String database,
        String username,
        String password,
        int minimumIdle,
        int maximumPoolSize,
        long connectionTimeoutMs,
        long idleTimeoutMs,
        long maxLifetimeMs) {

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }
}
