package com.glyph.core.economy.command;

import com.glyph.api.player.PlayerApi;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Resolves a command argument to a player UUID: exact online match first
 * (no I/O), then the players table for offline targets.
 */
final class PlayerNameResolver {

    record PlayerRef(UUID uuid, String name) { }

    private PlayerNameResolver() {
    }

    static CompletableFuture<Optional<PlayerRef>> resolve(PlayerApi players, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(
                    Optional.of(new PlayerRef(online.getUniqueId(), online.getName())));
        }
        return players.byUsername(name)
                .thenApply(profile -> profile.map(p -> new PlayerRef(p.uuid(), p.username())));
    }
}
