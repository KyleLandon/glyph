package com.glyph.api.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordTierTest {

    @Test
    void mapsLifetimeThresholds() {
        assertThat(DiscordTier.forLifetimeEarned(0)).isEmpty();
        assertThat(DiscordTier.forLifetimeEarned(10)).contains(DiscordTier.INITIATE);
        assertThat(DiscordTier.forLifetimeEarned(25)).contains(DiscordTier.SCOUT);
        assertThat(DiscordTier.forLifetimeEarned(50)).contains(DiscordTier.BLOODED);
        assertThat(DiscordTier.forLifetimeEarned(100)).contains(DiscordTier.VETERAN);
        assertThat(DiscordTier.forLifetimeEarned(250)).contains(DiscordTier.LEGEND);
        assertThat(DiscordTier.forLifetimeEarned(999)).contains(DiscordTier.LEGEND);
    }
}
