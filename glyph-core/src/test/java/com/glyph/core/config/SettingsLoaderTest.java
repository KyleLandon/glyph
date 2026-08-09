package com.glyph.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsLoaderTest {

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(content);
        } catch (Exception e) {
            throw new AssertionError("invalid test yaml", e);
        }
        return configuration;
    }

    private static final String CONFIG_YML = """
            server:
              id: test-01
            economy:
              starting-balance-minor: 2500
              currency-symbol: "€"
              hud:
                enabled: false
                title: "TEST"
            """;

    private static final String DATABASE_YML = """
            database:
              host: db.internal
              port: 5433
              name: glyphdb
              username: app
              password: filepw
              pool:
                minimum-idle: 3
                maximum-pool-size: 7
                connection-timeout-ms: 1234
                idle-timeout-ms: 5678
                max-lifetime-ms: 9012
            """;

    private static final String REDIS_YML = """
            redis:
              host: cache.internal
              port: 6380
              password: redispw
            """;

    @Test
    void loadsValuesFromYaml() {
        GlyphSettings settings = new SettingsLoader(Map.of())
                .load(yaml(CONFIG_YML), yaml(DATABASE_YML), yaml(REDIS_YML));

        assertThat(settings.serverId()).isEqualTo("test-01");

        DatabaseSettings db = settings.database();
        assertThat(db.host()).isEqualTo("db.internal");
        assertThat(db.port()).isEqualTo(5433);
        assertThat(db.database()).isEqualTo("glyphdb");
        assertThat(db.username()).isEqualTo("app");
        assertThat(db.password()).isEqualTo("filepw");
        assertThat(db.minimumIdle()).isEqualTo(3);
        assertThat(db.maximumPoolSize()).isEqualTo(7);
        assertThat(db.connectionTimeoutMs()).isEqualTo(1234);
        assertThat(db.idleTimeoutMs()).isEqualTo(5678);
        assertThat(db.maxLifetimeMs()).isEqualTo(9012);
        assertThat(db.jdbcUrl()).isEqualTo("jdbc:postgresql://db.internal:5433/glyphdb");

        RedisSettings redis = settings.redis();
        assertThat(redis.host()).isEqualTo("cache.internal");
        assertThat(redis.port()).isEqualTo(6380);
        assertThat(redis.password()).isEqualTo("redispw");
        assertThat(redis.hasPassword()).isTrue();

        EconomySettings economy = settings.economy();
        assertThat(economy.startingBalanceMinor()).isEqualTo(2500);
        assertThat(economy.currencySymbol()).isEqualTo("€");
        assertThat(economy.hudEnabled()).isFalse();
        assertThat(economy.hudTitle()).isEqualTo("TEST");
    }

    @Test
    void environmentVariablesOverrideYaml() {
        Map<String, String> env = Map.of(
                "GLYPH_SERVER_ID", "env-01",
                "GLYPH_DB_HOST", "env-db",
                "GLYPH_DB_PORT", "9999",
                "GLYPH_DB_PASSWORD", "envpw",
                "GLYPH_REDIS_HOST", "env-redis");

        GlyphSettings settings = new SettingsLoader(env)
                .load(yaml(CONFIG_YML), yaml(DATABASE_YML), yaml(REDIS_YML));

        assertThat(settings.serverId()).isEqualTo("env-01");
        assertThat(settings.database().host()).isEqualTo("env-db");
        assertThat(settings.database().port()).isEqualTo(9999);
        assertThat(settings.database().password()).isEqualTo("envpw");
        // Non-overridden values still come from YAML.
        assertThat(settings.database().username()).isEqualTo("app");
        assertThat(settings.redis().host()).isEqualTo("env-redis");
        assertThat(settings.redis().port()).isEqualTo(6380);
    }

    @Test
    void blankEnvironmentValuesAreIgnored() {
        GlyphSettings settings = new SettingsLoader(Map.of("GLYPH_DB_HOST", "  "))
                .load(yaml(CONFIG_YML), yaml(DATABASE_YML), yaml(REDIS_YML));

        assertThat(settings.database().host()).isEqualTo("db.internal");
    }

    @Test
    void missingSectionsFallBackToDefaults() {
        GlyphSettings settings = new SettingsLoader(Map.of())
                .load(new YamlConfiguration(), new YamlConfiguration(), new YamlConfiguration());

        assertThat(settings.serverId()).isEqualTo("glyph-01");
        assertThat(settings.database().host()).isEqualTo("localhost");
        assertThat(settings.database().port()).isEqualTo(5432);
        assertThat(settings.redis().port()).isEqualTo(6379);
        assertThat(settings.redis().hasPassword()).isFalse();
        assertThat(settings.economy().startingBalanceMinor()).isZero();
        assertThat(settings.economy().currencySymbol()).isEqualTo("$");
        assertThat(settings.economy().hudEnabled()).isTrue();
        assertThat(settings.economy().hudTitle()).isEqualTo("GLYPH");
    }

    @Test
    void invalidNumericEnvironmentValueFailsLoudly() {
        SettingsLoader loader = new SettingsLoader(Map.of("GLYPH_DB_PORT", "not-a-number"));

        assertThatThrownBy(() -> loader.load(yaml(CONFIG_YML), yaml(DATABASE_YML), yaml(REDIS_YML)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GLYPH_DB_PORT");
    }
}
