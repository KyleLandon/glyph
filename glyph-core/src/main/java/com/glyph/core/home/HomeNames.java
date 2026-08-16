package com.glyph.core.home;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Home name rules: short, lowercase, no spaces. */
public final class HomeNames {

    public static final String DEFAULT = "home";
    public static final int MAX_HOMES = 5;
    private static final Pattern VALID = Pattern.compile("^[a-z0-9_]{1,16}$");

    private HomeNames() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.of(DEFAULT);
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(name);
    }
}
