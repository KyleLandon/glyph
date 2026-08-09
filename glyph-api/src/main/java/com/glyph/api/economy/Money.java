package com.glyph.api.economy;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable, non-negative amount of currency in BIGINT minor units
 * (cents). Never floating point (GDD sections 14, 134).
 *
 * <p>Arithmetic uses exact math and throws {@link ArithmeticException} on
 * overflow instead of wrapping. Parsing is strict: it accepts what a player
 * could honestly mean ({@code 5}, {@code 5.5}, {@code 1,234.56}, {@code $10})
 * and rejects everything else — negatives, more than two decimals,
 * exponents, NaN-style text, surrounding garbage.</p>
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    /** Optional $, optional comma grouping, max 15 major digits, max 2 decimals. */
    private static final Pattern PARSE = Pattern.compile(
            "\\$?(\\d{1,15}|\\d{1,3}(?:,\\d{3}){0,4})(?:\\.(\\d{1,2}))?");

    private final long minorUnits;

    private Money(long minorUnits) {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + minorUnits);
        }
        this.minorUnits = minorUnits;
    }

    /** @throws IllegalArgumentException if {@code minorUnits} is negative */
    public static Money ofMinor(long minorUnits) {
        return new Money(minorUnits);
    }

    /**
     * Parses player input like {@code 5}, {@code 5.50}, {@code 1,234.56} or
     * {@code $10}.
     *
     * @throws IllegalArgumentException if the input is not a valid amount
     * @throws ArithmeticException      if the amount overflows a long
     */
    public static Money parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        Matcher matcher = PARSE.matcher(input.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a valid amount: " + input);
        }
        long major = Long.parseLong(matcher.group(1).replace(",", ""));
        String decimals = matcher.group(2);
        long cents = decimals == null ? 0
                : decimals.length() == 1 ? Long.parseLong(decimals) * 10
                : Long.parseLong(decimals);
        return new Money(Math.addExact(Math.multiplyExact(major, 100L), cents));
    }

    public long minorUnits() {
        return minorUnits;
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    /** @throws ArithmeticException on overflow */
    public Money plus(Money other) {
        return new Money(Math.addExact(minorUnits, other.minorUnits));
    }

    /** @throws IllegalArgumentException if the result would be negative */
    public Money minus(Money other) {
        long result = minorUnits - other.minorUnits;
        if (result < 0) {
            throw new IllegalArgumentException(
                    "Insufficient amount: " + this + " - " + other);
        }
        return new Money(result);
    }

    public boolean isLessThan(Money other) {
        return minorUnits < other.minorUnits;
    }

    /** Formats with symbol and grouping: {@code $1,234.56}. */
    public String format(String currencySymbol) {
        return currencySymbol + String.format(Locale.US, "%,d.%02d",
                minorUnits / 100, minorUnits % 100);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(minorUnits, other.minorUnits);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Money other && minorUnits == other.minorUnits;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(minorUnits);
    }

    @Override
    public String toString() {
        return format("$");
    }
}
