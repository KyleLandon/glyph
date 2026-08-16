package com.glyph.core.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.Money;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class TabListDisplayTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void formatRowIncludesTitleNameMoneyGlyphsAndDeaths() {
        String text = PLAIN.serialize(TabListDisplay.formatRow(
                "Steve", net.kyori.adventure.text.format.NamedTextColor.GOLD,
                "Blooded", Money.of(12_400), 13L, "✦", true, 29, "$"));
        assertThat(text).isEqualTo("[Blooded] Steve $ 12.4K ✦13 ☠29");
    }

    @Test
    void formatRowUsesPeasantWhenUntitled() {
        String text = PLAIN.serialize(TabListDisplay.formatRow(
                "KyleLandon", net.kyori.adventure.text.format.NamedTextColor.WHITE,
                null, Money.of(274), 0L, "✦", true, 6, "$"));
        assertThat(text).isEqualTo("[Peasant] KyleLandon $ 274 ✦0 ☠6");
    }

    @Test
    void formatRowShowsPlaceholderWhenMoneyUnknown() {
        String text = PLAIN.serialize(TabListDisplay.formatRow(
                "Alex", net.kyori.adventure.text.format.NamedTextColor.WHITE,
                null, null, null, "✦", true, 0, "$"));
        assertThat(text).contains("Alex");
        assertThat(text).contains("$ —");
        assertThat(text).contains("✦");
        assertThat(text).contains("☠");
        assertThat(text).startsWith("[Peasant] Alex");
    }

    @Test
    void formatRowOmitsGlyphsWhenDisabled() {
        String text = PLAIN.serialize(TabListDisplay.formatRow(
                "Steve", net.kyori.adventure.text.format.NamedTextColor.WHITE,
                null, Money.of(100), 3L, "✦", false, 2, "$"));
        assertThat(text).isEqualTo("[Peasant] Steve $ 100 ☠2");
    }
}
