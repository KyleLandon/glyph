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
              starting-balance: 25
              currency-symbol: "€"
              hud:
                enabled: false
                title: "TEST"
            auction:
              enabled: false
              listing-fee-percent: 2.5
              sale-fee-percent: 7.0
              max-listings-per-player: 3
              duration-hours: 12
            bounties:
              enabled: false
              minimum: 50
              same-victim-cooldown-minutes: 30
            rewards:
              playtime:
                enabled: false
                interval-minutes: 10
                amount: 5
                min-activity: 40
            glyphs:
              enabled: false
              symbol: "✦"
              first-bounty-reward: 3
            discord:
              invite-url: "https://discord.gg/test"
            starter:
              enabled: false
              items:
                - stone_hoe
                - apple:8
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
        assertThat(economy.startingBalance()).isEqualTo(25);
        assertThat(economy.currencySymbol()).isEqualTo("€");
        assertThat(economy.hudEnabled()).isFalse();
        assertThat(economy.hudTitle()).isEqualTo("TEST");

        AuctionSettings auction = settings.auction();
        assertThat(auction.enabled()).isFalse();
        assertThat(auction.listingFeeBasisPoints()).isEqualTo(250);
        assertThat(auction.saleFeeBasisPoints()).isEqualTo(700);
        assertThat(auction.maxListingsPerPlayer()).isEqualTo(3);
        assertThat(auction.durationHours()).isEqualTo(12);

        BountySettings bounties = settings.bounties();
        assertThat(bounties.enabled()).isFalse();
        assertThat(bounties.minimum()).isEqualTo(50);
        assertThat(bounties.sameVictimCooldownMinutes()).isEqualTo(30);

        PlaytimeRewardSettings rewards = settings.rewards();
        assertThat(rewards.enabled()).isFalse();
        assertThat(rewards.intervalMinutes()).isEqualTo(10);
        assertThat(rewards.amount()).isEqualTo(5);
        assertThat(rewards.minActivity()).isEqualTo(40);
        assertThat(rewards.minActivityUnits()).isEqualTo(4000);

        GlyphCurrencySettings glyphs = settings.glyphs();
        assertThat(glyphs.enabled()).isFalse();
        assertThat(glyphs.symbol()).isEqualTo("✦");
        assertThat(glyphs.firstBountyReward()).isEqualTo(3);

        assertThat(settings.discord().inviteUrl()).isEqualTo("https://discord.gg/test");
        assertThat(settings.chat().itemPlaceholders()).isTrue();

        StarterSettings starter = settings.starter();
        assertThat(starter.enabled()).isFalse();
        assertThat(starter.items()).extracting(StarterSettings.StarterItem::material)
                .containsExactly(org.bukkit.Material.STONE_HOE, org.bukkit.Material.APPLE);
        assertThat(starter.items().get(1).amount()).isEqualTo(8);
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
        assertThat(settings.economy().startingBalance()).isEqualTo(100);
        assertThat(settings.economy().currencySymbol()).isEqualTo("$");
        assertThat(settings.economy().hudEnabled()).isTrue();
        assertThat(settings.economy().hudTitle()).isEqualTo("GLYPH");
        assertThat(settings.tab().enabled()).isTrue();
        assertThat(settings.tab().header()).isEqualTo("GLYPH");
        assertThat(settings.tab().footer()).isEqualTo("play.glyphmc.net");
        assertThat(settings.auction().enabled()).isTrue();
        assertThat(settings.auction().listingFeeBasisPoints()).isEqualTo(100);
        assertThat(settings.auction().saleFeeBasisPoints()).isEqualTo(500);
        assertThat(settings.auction().maxListingsPerPlayer()).isEqualTo(10);
        assertThat(settings.auction().durationHours()).isEqualTo(48);
        assertThat(settings.bounties().enabled()).isTrue();
        assertThat(settings.bounties().minimum()).isEqualTo(100);
        assertThat(settings.bounties().sameVictimCooldownMinutes()).isEqualTo(60);
        assertThat(settings.rewards().enabled()).isTrue();
        assertThat(settings.rewards().intervalMinutes()).isEqualTo(15);
        assertThat(settings.rewards().amount()).isEqualTo(10);
        assertThat(settings.rewards().minActivity()).isEqualTo(20);
        assertThat(settings.glyphs().enabled()).isTrue();
        assertThat(settings.glyphs().symbol()).isEqualTo("✦");
        assertThat(settings.glyphs().firstBountyReward()).isEqualTo(3);
        assertThat(settings.discord().inviteUrl()).isEqualTo("https://discord.gg/htkQHR4gdf");
        assertThat(settings.chat().itemPlaceholders()).isTrue();
        assertThat(settings.starter().enabled()).isTrue();
        assertThat(settings.starter().items()).isEqualTo(StarterSettings.defaults());
    }

    @Test
    void invalidNumericEnvironmentValueFailsLoudly() {
        SettingsLoader loader = new SettingsLoader(Map.of("GLYPH_DB_PORT", "not-a-number"));

        assertThatThrownBy(() -> loader.load(yaml(CONFIG_YML), yaml(DATABASE_YML), yaml(REDIS_YML)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GLYPH_DB_PORT");
    }

    @Test
    void legacyStartingBalanceMinorIsConvertedToDollars() {
        GlyphSettings settings = new SettingsLoader(Map.of()).load(yaml("""
                economy:
                  starting-balance-minor: 10000
                """), new YamlConfiguration(), new YamlConfiguration());

        assertThat(settings.economy().startingBalance()).isEqualTo(100);
    }
}
