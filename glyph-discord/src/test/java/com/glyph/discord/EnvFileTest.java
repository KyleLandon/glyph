package com.glyph.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvFileTest {

    @Test
    void mapsFriendlyAliases() {
        Map<String, String> canonical = EnvFile.canonicalize(Map.of(
                "GlyphBotToken", "abc.token",
                "DiscordServerID", "12345"));
        assertThat(canonical.get("GLYPH_DISCORD_TOKEN")).isEqualTo("abc.token");
        assertThat(canonical.get("GLYPH_DISCORD_GUILD_ID")).isEqualTo("12345");
    }
}
