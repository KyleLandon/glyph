package com.glyph.core.smp.tpa;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory TPA requests. One outgoing and one incoming per player. */
public final class TpaService {

    public enum Kind { HERE, THERE }

    public record Request(UUID from, UUID to, Kind kind, Instant expires) { }

    private final Map<UUID, Request> outgoing = new ConcurrentHashMap<>();
    private final Map<UUID, Request> incoming = new ConcurrentHashMap<>();

    public Optional<String> request(UUID from, UUID to, Kind kind, Instant expires) {
        if (from.equals(to)) {
            return Optional.of("You cannot teleport to yourself.");
        }
        Request existing = outgoing.get(from);
        if (existing != null && Instant.now().isBefore(existing.expires())) {
            return Optional.of("You already have a pending teleport request.");
        }
        Request request = new Request(from, to, kind, expires);
        outgoing.put(from, request);
        incoming.put(to, request);
        return Optional.empty();
    }

    public Optional<Request> incoming(UUID player) {
        Request request = incoming.get(player);
        if (request == null) {
            return Optional.empty();
        }
        if (!Instant.now().isBefore(request.expires())) {
            clear(request);
            return Optional.empty();
        }
        return Optional.of(request);
    }

    public void clear(Request request) {
        outgoing.remove(request.from(), request);
        incoming.remove(request.to(), request);
    }

    public void clearPlayer(UUID player) {
        Request out = outgoing.remove(player);
        if (out != null) {
            incoming.remove(out.to(), out);
        }
        Request in = incoming.remove(player);
        if (in != null) {
            outgoing.remove(in.from(), in);
        }
    }
}
