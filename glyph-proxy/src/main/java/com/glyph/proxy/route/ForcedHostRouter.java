package com.glyph.proxy.route;

import java.util.Locale;
import java.util.Optional;

/**
 * Maps the handshake hostname to a Velocity backend. Minecraft SRV clients
 * send the SRV <em>target</em>, so DNS must point each
 * {@code _minecraft._tcp.<name>} at {@code <name>.glyphmc.net} (not the
 * shared playit host) or every join looks like the tunnel name and lands
 * on the try-list (anarchy).
 */
public final class ForcedHostRouter {

    private ForcedHostRouter() {
    }

    public static Optional<String> backendForHost(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            normalized = normalized.substring(0, colon);
        }
        if (normalized.equals("smp.glyphmc.net") || normalized.equals("smp")) {
            return Optional.of("smp");
        }
        if (normalized.equals("anarchy.glyphmc.net")
                || normalized.equals("anarchy")
                || normalized.equals("play.glyphmc.net")
                || normalized.equals("play")) {
            return Optional.of("anarchy");
        }
        return Optional.empty();
    }
}
