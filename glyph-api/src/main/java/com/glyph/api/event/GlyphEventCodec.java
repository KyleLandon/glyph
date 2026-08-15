package com.glyph.api.event;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal JSON codec for Glyph Redis events (no Jackson dependency in glyph-api).
 */
public final class GlyphEventCodec {

    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern UUID_FIELD = Pattern.compile("\"uuid\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LIFETIME = Pattern.compile("\"lifetimeEarned\"\\s*:\\s*(-?\\d+)");
    private static final Pattern DISCORD_ID = Pattern.compile("\"discordUserId\"\\s*:\\s*(-?\\d+)");

    private GlyphEventCodec() {
    }

    public static String lifetime(UUID uuid, long lifetimeEarned) {
        return "{\"type\":\"" + GlyphEventType.GLYPH_LIFETIME.wireName()
                + "\",\"uuid\":\"" + uuid
                + "\",\"lifetimeEarned\":" + lifetimeEarned + "}";
    }

    public static String discordLinked(UUID uuid, long discordUserId) {
        return "{\"type\":\"" + GlyphEventType.DISCORD_LINKED.wireName()
                + "\",\"uuid\":\"" + uuid
                + "\",\"discordUserId\":" + discordUserId + "}";
    }

    public static Optional<GlyphLifetimeEvent> parseLifetime(String json) {
        Optional<GlyphEventType> type = typeOf(json);
        if (type.isEmpty() || type.get() != GlyphEventType.GLYPH_LIFETIME) {
            return Optional.empty();
        }
        Optional<UUID> uuid = uuidOf(json);
        Matcher lifetime = LIFETIME.matcher(json);
        if (uuid.isEmpty() || !lifetime.find()) {
            return Optional.empty();
        }
        return Optional.of(new GlyphLifetimeEvent(uuid.get(), Long.parseLong(lifetime.group(1))));
    }

    public static Optional<DiscordLinkedEvent> parseDiscordLinked(String json) {
        Optional<GlyphEventType> type = typeOf(json);
        if (type.isEmpty() || type.get() != GlyphEventType.DISCORD_LINKED) {
            return Optional.empty();
        }
        Optional<UUID> uuid = uuidOf(json);
        Matcher discord = DISCORD_ID.matcher(json);
        if (uuid.isEmpty() || !discord.find()) {
            return Optional.empty();
        }
        return Optional.of(new DiscordLinkedEvent(uuid.get(), Long.parseLong(discord.group(1))));
    }

    public static Optional<GlyphEventType> typeOf(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TYPE.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return GlyphEventType.fromWire(matcher.group(1));
    }

    private static Optional<UUID> uuidOf(String json) {
        Matcher matcher = UUID_FIELD.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(matcher.group(1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record GlyphLifetimeEvent(UUID uuid, long lifetimeEarned) {
    }

    public record DiscordLinkedEvent(UUID uuid, long discordUserId) {
    }
}
