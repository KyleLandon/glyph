package com.glyph.core.config;

/**
 * Discord companion settings exposed to players (invite URL in /linkdiscord).
 * Bot token and role IDs live only in the glyph-discord process.
 */
public record DiscordSettings(String inviteUrl) {
}
