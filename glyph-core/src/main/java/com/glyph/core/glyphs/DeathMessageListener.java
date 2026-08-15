package com.glyph.core.glyphs;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Custom death messages from equipped Glyph death styles. */
public final class DeathMessageListener implements Listener {

    private final GlyphsService glyphs;

    public DeathMessageListener(GlyphsService glyphs) {
        this.glyphs = Objects.requireNonNull(glyphs, "glyphs");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        glyphs.deathStyleProductId(victim.getUniqueId()).flatMap(GlyphDeathStyles::templateForProductId)
                .ifPresent(template -> {
                    String message = GlyphDeathStyles.format(
                            template, victim.getName(), killer.getName());
                    event.deathMessage(Component.text(message, NamedTextColor.GRAY));
                });
    }
}
