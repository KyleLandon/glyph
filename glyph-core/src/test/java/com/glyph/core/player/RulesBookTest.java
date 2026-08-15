package com.glyph.core.player;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class RulesBookTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void bookHasSixPagesCoveringRulesEconomyAndCheats() {
        String all = RulesBook.pages().stream()
                .map(PLAIN::serialize)
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(RulesBook.pages()).hasSize(6);
        assertThat(all).contains("No land claims");
        assertThat(all).contains("No grief protection");
        assertThat(all).contains("spawn zone");
        assertThat(all).contains("/ah");
        assertThat(all).contains("/bounty");
        assertThat(all).contains("/rules");
        assertThat(all).contains("killaura");
        assertThat(all).contains("glyphmc.net");
    }
}
