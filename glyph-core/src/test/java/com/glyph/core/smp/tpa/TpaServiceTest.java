package com.glyph.core.smp.tpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.core.smp.tpa.TpaService.Kind;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TpaServiceTest {

    private final UUID alice = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID bob = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void storesAndExpiresIncoming() {
        TpaService tpa = new TpaService();
        assertThat(tpa.request(alice, bob, Kind.THERE, Instant.now().plusSeconds(60))).isEmpty();
        assertThat(tpa.incoming(bob)).isPresent();
        assertThat(tpa.incoming(bob).orElseThrow().from()).isEqualTo(alice);
        tpa.clear(tpa.incoming(bob).orElseThrow());
        assertThat(tpa.incoming(bob)).isEmpty();
    }

    @Test
    void rejectsSelfAndDuplicateOutgoing() {
        TpaService tpa = new TpaService();
        assertThat(tpa.request(alice, alice, Kind.THERE, Instant.now().plusSeconds(60))).isPresent();
        assertThat(tpa.request(alice, bob, Kind.THERE, Instant.now().plusSeconds(60))).isEmpty();
        assertThat(tpa.request(alice, bob, Kind.HERE, Instant.now().plusSeconds(60))).isPresent();
    }
}
