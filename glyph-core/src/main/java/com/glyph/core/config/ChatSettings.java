package com.glyph.core.config;

/** Chat helpers: item placeholders and local / global channels. */
public record ChatSettings(boolean itemPlaceholders, boolean localEnabled, double localRadius) {

    public boolean localChat() {
        return localEnabled && localRadius > 0;
    }
}
