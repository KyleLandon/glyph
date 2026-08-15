package com.glyph.core.discord;

import java.security.SecureRandom;

/** Generates short-lived player-facing Discord link codes. */
public final class DiscordLinkCodes {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private DiscordLinkCodes() {
    }

    /** Format: {@code GLYPH-XXXXXX} (excludes ambiguous 0/O/1/I). */
    public static String generate() {
        char[] body = new char[6];
        for (int i = 0; i < body.length; i++) {
            body[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return "GLYPH-" + new String(body);
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase();
    }
}
