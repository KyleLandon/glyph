package com.glyph.core.config;

/**
 * Redis connection settings. Values come from {@code redis.yml} with
 * {@code GLYPH_REDIS_*} environment overrides.
 */
public record RedisSettings(String host, int port, String password) {

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
