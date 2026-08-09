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
            "5,           500",
            "5.5,         550",
            "5.50,        550",
            "0.01,        1",
            "0,           0",
            "1234.56,     123456",
            "'1,234.56',  123456",
            "$10,         1000",
            "'$1,000',    100000",
            "' 25 ',      2500",
    })
    void parsesValidAmounts(String input, long expectedMinor) {
        assertThat(Money.parse(input).minorUnits()).isEqualTo(expectedMinor);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-5", "-0.01",              // negative
            "NaN", "Infinity",          // NaN-style
            "1e10", "0x10",             // exponents / hex
            "5.555", "5.",              // bad decimals
            "", " ", "abc", "5 dollars", // garbage
            "５０",                      // alternate characters (fullwidth)
            "1,00.00",                  // broken grouping
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
    void negativeMinorUnitsRejected() {
        assertThatThrownBy(() -> Money.ofMinor(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void additionOverflowThrows() {
        Money nearMax = Money.ofMinor(Long.MAX_VALUE);
        assertThatThrownBy(() -> nearMax.plus(Money.ofMinor(1)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void subtractionBelowZeroThrows() {
        assertThatThrownBy(() -> Money.ofMinor(100).minus(Money.ofMinor(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arithmeticWorks() {
        assertThat(Money.ofMinor(150).plus(Money.ofMinor(50))).isEqualTo(Money.ofMinor(200));
        assertThat(Money.ofMinor(150).minus(Money.ofMinor(50))).isEqualTo(Money.ofMinor(100));
        assertThat(Money.ofMinor(100).isLessThan(Money.ofMinor(101))).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.ofMinor(1).isPositive()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "0,        $0.00",
            "1,        $0.01",
            "550,      $5.50",
            "123456,   '$1,234.56'",
            "100000000, '$1,000,000.00'",
    })
    void formatsWithGroupingAndSymbol(long minor, String expected) {
        assertThat(Money.ofMinor(minor).format("$")).isEqualTo(expected);
    }

    @Test
    void parseFormatRoundTrips() {
        Money original = Money.parse("1,234.56");
        assertThat(Money.parse(original.format("$"))).isEqualTo(original);
    }
}
