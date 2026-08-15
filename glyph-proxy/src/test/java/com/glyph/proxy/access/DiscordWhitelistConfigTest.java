package com.glyph.proxy.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordWhitelistConfigTest {

    @Test
    void defaultsDisabled() {
        DiscordWhitelistConfig config = DiscordWhitelistConfig.fromEnv();
        // Without env override in the test process this should stay false.
        assertThat(config.enabled()).isFalse();
        assertThat(config.jdbcUrl()).contains("jdbc:postgresql://");
        assertThat(config.inviteUrl()).isNotBlank();
    }
}
