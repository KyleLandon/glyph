package com.glyph.core.command;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class RestartCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void firstAnnouncementNamesTheInitiator() {
        String text = PLAIN.serialize(RestartCommand.announcement(10, "KyleLandon"));
        assertThat(text).contains("KyleLandon");
        assertThat(text).contains("10 seconds");
    }

    @Test
    void laterAnnouncementsAreJustTheCountdown() {
        assertThat(PLAIN.serialize(RestartCommand.announcement(3, null)))
                .isEqualTo("Restarting in 3 seconds.");
        assertThat(PLAIN.serialize(RestartCommand.announcement(1, null)))
                .isEqualTo("Restarting in 1 second.");
    }
}
