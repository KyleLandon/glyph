package com.glyph.proxy.route;

/**
 * MiniMessage MOTDs for the server list. Hostname picks the copy; unknown
 * hosts (localhost, raw IP) use anarchy because that is the try-list default.
 */
public final class MotdCatalog {

    public static final String ANARCHY =
            "<gold><bold>GLYPH</bold></gold> <gray>Anarchy</gray>"
                    + "<newline><dark_gray>No claims. Trust carefully.</dark_gray>";

    public static final String SMP =
            "<gold><bold>GLYPH</bold></gold> <aqua>Forever World</aqua>"
                    + "<newline><dark_gray>Hang out. Build. Stay.</dark_gray>";

    private MotdCatalog() {
    }

    public static String miniMessageForHost(String host) {
        return ForcedHostRouter.backendForHost(host).orElse("anarchy").equals("smp")
                ? SMP
                : ANARCHY;
    }
}
