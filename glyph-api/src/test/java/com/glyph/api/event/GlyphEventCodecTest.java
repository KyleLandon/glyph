package com.glyph.api.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GlyphEventCodecTest {

    @Test
    void roundTripsLifetimeEvent() {
        UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String json = GlyphEventCodec.lifetime(uuid, 67L);
        assertThat(GlyphEventCodec.parseLifetime(json)).hasValueSatisfying(event -> {
            assertThat(event.uuid()).isEqualTo(uuid);
            assertThat(event.lifetimeEarned()).isEqualTo(67L);
        });
    }

    @Test
    void roundTripsTitleEvent() {
        UUID uuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String json = GlyphEventCodec.title(uuid);
        assertThat(GlyphEventCodec.parseTitle(json)).hasValueSatisfying(event ->
                assertThat(event.uuid()).isEqualTo(uuid));
    }

    @Test
    void roundTripsDiscordLinkedEvent() {
        UUID uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String json = GlyphEventCodec.discordLinked(uuid, 123456789012345678L);
        assertThat(GlyphEventCodec.parseDiscordLinked(json)).hasValueSatisfying(event -> {
            assertThat(event.uuid()).isEqualTo(uuid);
            assertThat(event.discordUserId()).isEqualTo(123456789012345678L);
        });
    }
}
