package com.glyph.core.chat;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ChatChannelsTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void localAndGlobalLinesIncludeChannelPrefix() {
        assertThat(PLAIN.serialize(ChatChannels.localLine(
                Component.text("Rose"), Component.text("hello"))))
                .isEqualTo("[Local] Rose: hello");
        assertThat(PLAIN.serialize(ChatChannels.globalLine(
                Component.text("Rose"), Component.text("hello"))))
                .isEqualTo("[Global] Rose: hello");
    }
}
