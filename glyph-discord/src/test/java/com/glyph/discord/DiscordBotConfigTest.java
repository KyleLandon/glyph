package com.glyph.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glyph.api.discord.DiscordTier;
import com.glyph.api.glyphs.GlyphTitle;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiscordBotConfigTest {

    @Test
    void loadsRequiredAndOptionalRoleIds() {
        Map<String, String> env = new HashMap<>();
        env.put("GLYPH_DISCORD_TOKEN", "test-token");
        env.put("GLYPH_DISCORD_GUILD_ID", "111");
        env.put("GLYPH_DISCORD_ROLE_VERIFIED", "222");
        env.put("GLYPH_DISCORD_ROLE_ALPHA", "333");
        env.put("GLYPH_DISCORD_ROLE_SCOUT", "444");
        env.put("GLYPH_DISCORD_ROLE_TITLE_HUNTER", "555");
        env.put("GLYPH_DB_HOST", "db.example");
        env.put("GLYPH_DB_PORT", "5433");

        DiscordBotConfig config = DiscordBotConfig.fromEnv(map(env));
        assertThat(config.token()).isEqualTo("test-token");
        assertThat(config.guildId()).isEqualTo(111L);
        assertThat(config.verifiedRoleId()).isEqualTo(222L);
        assertThat(config.alphaRoleId()).isEqualTo(333L);
        assertThat(config.tierRoleIds()).containsEntry(DiscordTier.SCOUT, 444L);
        assertThat(config.titleRoleIds()).containsEntry(GlyphTitle.HUNTER, 555L);
        assertThat(config.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example:5433/glyph");
    }

    @Test
    void roleIdsOptionalWhenBootstrapWillCreateThem() {
        DiscordBotConfig config = DiscordBotConfig.fromEnv(map(Map.of(
                "GLYPH_DISCORD_TOKEN", "test-token",
                "GLYPH_DISCORD_GUILD_ID", "111")));
        assertThat(config.verifiedRoleId()).isZero();
        assertThat(config.alphaRoleId()).isZero();
        assertThat(config.tierRoleIds()).isEmpty();
        assertThat(config.titleRoleIds()).isEmpty();
    }

    @Test
    void missingTokenFails() {
        assertThatThrownBy(() -> DiscordBotConfig.fromEnv(map(Map.of(
                        "GLYPH_DISCORD_GUILD_ID", "1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GLYPH_DISCORD_TOKEN");
    }

    private static DiscordBotConfig.MapLike map(Map<String, String> values) {
        return new DiscordBotConfig.MapLike() {
            @Override
            public String get(String key) {
                return values.get(key);
            }

            @Override
            public String getOrDefault(String key, String def) {
                String value = values.get(key);
                return (value == null || value.isBlank()) ? def : value;
            }
        };
    }
}
