package com.glyph.core.smp.warp;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class WarpNames {

    public static final String DEFAULT_LIST = "list";
    private static final Pattern VALID = Pattern.compile("^[a-z0-9_]{1,16}$");
    private static final Set<String> RESERVED = Set.of(
            "list", "set", "delete", "del", "remove", "help", "spawn", "wild", "home");

    private WarpNames() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(name).matches() || RESERVED.contains(name)) {
            return Optional.empty();
        }
        return Optional.of(name);
    }
}
