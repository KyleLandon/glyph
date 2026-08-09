package com.glyph.api.economy;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable, non-negative amount of currency in BIGINT whole dollars.
 * There are no cents anywhere in the economy — the smallest unit of money
 * is $1. Never floating point (GDD sections 14, 134).
 *
 * <p>Arithmetic uses exact math and throws {@link ArithmeticException} on
 * overflow instead of wrapping. Parsing is strict: it accepts what a player
 * could honestly mean ({@code 5}, {@code 1,234}, {@code $10}) and rejects
 * everything else — negatives, decimals, exponents, NaN-style text,
 * surrounding garbage.</p>
 */
public final class Money implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    /** Optional $, optional comma grouping, max 15 digits, no decimals. */
    private static final Pattern PARSE = Pattern.compile(
            "\\$?(\\d{1,15}|\\d{1,3}(?:,\\d{3}){0,4})");

    private final long dollars;

    private Money(long dollars) {
        if (dollars < 0) {
            throw new IllegalArgumentException("Money cannot be negative: " + dollars);
        }
        this.dollars = dollars;
    }

    /** @throws IllegalArgumentException if {@code dollars} is negative */
    public static Money of(long dollars) {
        return new Money(dollars);
    }

    /**
     * Parses player input like {@code 5}, {@code 1,234} or {@code $10}.
     * Decimals are rejected — the economy has no cents.
     *
     * @throws IllegalArgumentException if the input is not a valid amount
     */
    public static Money parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        Matcher matcher = PARSE.matcher(input.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a valid amount: " + input);
        }
        return new Money(Long.parseLong(matcher.group(1).replace(",", "")));
    }

    public long dollars() {
        return dollars;
    }

    public boolean isZero() {
        return dollars == 0;
    }

    public boolean isPositive() {
        return dollars > 0;
    }

    /** @throws ArithmeticException on overflow */
    public Money plus(Money other) {
        return new Money(Math.addExact(dollars, other.dollars));
    }

    /** @throws IllegalArgumentException if the result would be negative */
    public Money minus(Money other) {
        long result = dollars - other.dollars;
        if (result < 0) {
            throw new IllegalArgumentException(
                    "Insufficient amount: " + this + " - " + other);
        }
        return new Money(result);
    }

    public boolean isLessThan(Money other) {
        return dollars < other.dollars;
    }

    /** Formats with symbol and grouping: {@code $1,234}. */
    public String format(String currencySymbol) {
        return currencySymbol + String.format(Locale.US, "%,d", dollars);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(dollars, other.dollars);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Money other && dollars == other.dollars;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(dollars);
    }

    @Override
    public String toString() {
        return format("$");
    }
}
