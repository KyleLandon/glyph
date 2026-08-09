package com.glyph.core.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.glyph.api.economy.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * GDD section 84 mandates Money tests for parsing, formatting, negative
 * values and overflow; section 85 adds hostile input (NaN-style, huge,
 * alternate characters).
 */
class MoneyTest {

    @ParameterizedTest
    @CsvSource({
            "5,          5",
            "0,          0",
            "1234,       1234",
            "'1,234',    1234",
            "$10,        10",
            "'$1,000',   1000",
            "' 25 ',     25",
    })
    void parsesValidAmounts(String input, long expectedDollars) {
        assertThat(Money.parse(input).dollars()).isEqualTo(expectedDollars);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-5", "-1",                 // negative
            "NaN", "Infinity",          // NaN-style
            "1e10", "0x10",             // exponents / hex
            "5.50", "5.", "0.01",       // decimals: the economy has no cents
            "", " ", "abc", "5 dollars", // garbage
            "５０",                      // alternate characters (fullwidth)
            "1,00",                     // broken grouping
            "999999999999999999999999", // extremely large
            "+5",                       // explicit sign
    })
    void rejectsHostileInput(String input) {
        assertThatThrownBy(() -> Money.parse(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInputRejected() {
        assertThatThrownBy(() -> Money.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeDollarsRejected() {
        assertThatThrownBy(() -> Money.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void additionOverflowThrows() {
        Money nearMax = Money.of(Long.MAX_VALUE);
        assertThatThrownBy(() -> nearMax.plus(Money.of(1)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void subtractionBelowZeroThrows() {
        assertThatThrownBy(() -> Money.of(100).minus(Money.of(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arithmeticWorks() {
        assertThat(Money.of(150).plus(Money.of(50))).isEqualTo(Money.of(200));
        assertThat(Money.of(150).minus(Money.of(50))).isEqualTo(Money.of(100));
        assertThat(Money.of(100).isLessThan(Money.of(101))).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of(1).isPositive()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0,        $0",
            "1,        $1",
            "550,      $550",
            "1234,     '$1,234'",
            "1000000,  '$1,000,000'",
    })
    void formatsWithGroupingAndSymbol(long dollars, String expected) {
        assertThat(Money.of(dollars).format("$")).isEqualTo(expected);
    }

    @Test
    void parseFormatRoundTrips() {
        Money original = Money.parse("1,234");
        assertThat(Money.parse(original.format("$"))).isEqualTo(original);
    }
}
