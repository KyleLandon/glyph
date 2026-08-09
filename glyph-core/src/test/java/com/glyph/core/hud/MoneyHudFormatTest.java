package com.glyph.core.hud;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.Money;
import org.junit.jupiter.api.Test;

class MoneyHudFormatTest {

    @Test
    void formatsSmallAmountsWithGrouping() {
        assertThat(MoneyHud.formatHud(Money.of(0), "$")).isEqualTo("$ 0");
        assertThat(MoneyHud.formatHud(Money.of(100), "$")).isEqualTo("$ 100");
        assertThat(MoneyHud.formatHud(Money.of(9_999), "$")).isEqualTo("$ 9,999");
    }

    @Test
    void abbreviatesThousandsMillionsBillions() {
        assertThat(MoneyHud.formatHud(Money.of(10_000), "$")).isEqualTo("$ 10K");
        assertThat(MoneyHud.formatHud(Money.of(12_500), "$")).isEqualTo("$ 12.5K");
        assertThat(MoneyHud.formatHud(Money.of(1_600_000), "$")).isEqualTo("$ 1.6M");
        assertThat(MoneyHud.formatHud(Money.of(2_000_000_000L), "$")).isEqualTo("$ 2B");
    }
}
