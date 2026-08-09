package com.glyph.core.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.Money;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class TabListDisplayTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void formatRowIncludesNameMoneyAndDeaths() {
        String text = PLAIN.serialize(
                TabListDisplay.formatRow("Steve", Money.of(100), 3, "$"));
        assertThat(text).isEqualTo("Steve  $ 100  3☠");
    }

    @Test
    void formatRowShowsPlaceholderWhenMoneyUnknown() {
        String text = PLAIN.serialize(
                TabListDisplay.formatRow("Alex", null, 0, "$"));
        assertThat(text).isEqualTo("Alex  $ —  0☠");
    }
}
