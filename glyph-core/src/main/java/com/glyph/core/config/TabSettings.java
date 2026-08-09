package com.glyph.core.config;

/**
 * Tab list (player list) presentation.
 *
 * @param enabled whether custom tab list names / header / footer are applied
 * @param header  top banner shown when holding Tab
 * @param footer  bottom banner shown when holding Tab
 */
public record TabSettings(boolean enabled, String header, String footer) {
}
