package com.glyph.core.nick;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Character-name rules for {@code /nickname} on the Forever World. */
public final class NicknameNames {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 16;
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 _-]{0,15}$");
    private static final Set<String> RESERVED = Set.of("off", "reset", "clear", "none", "nick", "nickname");

    private NicknameNames() {
    }

    public static boolean isClearToken(String raw) {
        return raw != null && RESERVED.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String collapsed = raw.trim().replaceAll(" +", " ");
        if (collapsed.length() < MIN_LENGTH || collapsed.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (isClearToken(collapsed) || !VALID.matcher(collapsed).matches()) {
            return Optional.empty();
        }
        return Optional.of(collapsed);
    }
}
