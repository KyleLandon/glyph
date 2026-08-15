package com.glyph.discord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads {@code KEY=value} secrets files for local bot runs. */
public final class EnvFile {

    private EnvFile() {
    }

    public static Map<String, String> loadFirstExisting(List<Path> candidates) throws IOException {
        for (Path path : candidates) {
            if (path != null && Files.isRegularFile(path)) {
                return load(path);
            }
        }
        return Map.of();
    }

    public static Map<String, String> load(Path path) throws IOException {
        Map<String, String> raw = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            raw.put(key, value);
        }
        return canonicalize(raw);
    }

    /** Maps friendly aliases onto the {@code GLYPH_*} names the bot expects. */
    static Map<String, String> canonicalize(Map<String, String> raw) {
        Map<String, String> out = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = normalizeKey(entry.getKey());
            out.put(key, entry.getValue());
        }
        copyAlias(out, "GlyphBotToken", "GLYPH_DISCORD_TOKEN");
        copyAlias(out, "DISCORD_BOT_TOKEN", "GLYPH_DISCORD_TOKEN");
        copyAlias(out, "DiscordServerID", "GLYPH_DISCORD_GUILD_ID");
        copyAlias(out, "DISCORD_GUILD_ID", "GLYPH_DISCORD_GUILD_ID");
        return out;
    }

    private static void copyAlias(Map<String, String> map, String from, String to) {
        if (!map.containsKey(to) || map.get(to) == null || map.get(to).isBlank()) {
            String value = map.get(from);
            if (value != null && !value.isBlank()) {
                map.put(to, value);
            }
        }
    }

    private static String normalizeKey(String key) {
        // Keep GLYPH_* as-is; leave other keys for alias mapping.
        if (key.toUpperCase(Locale.ROOT).startsWith("GLYPH_")) {
            return key.toUpperCase(Locale.ROOT);
        }
        return key;
    }

    public static DiscordBotConfig.MapLike merge(Map<String, String> file, Map<String, String> env) {
        return new DiscordBotConfig.MapLike() {
            @Override
            public String get(String key) {
                String fromFile = file.get(key);
                if (fromFile != null && !fromFile.isBlank()) {
                    return fromFile;
                }
                return env.get(key);
            }

            @Override
            public String getOrDefault(String key, String def) {
                String value = get(key);
                return (value == null || value.isBlank()) ? def : value;
            }
        };
    }
}
