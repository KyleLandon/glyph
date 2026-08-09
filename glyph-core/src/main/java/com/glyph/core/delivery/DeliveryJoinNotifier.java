package com.glyph.core.delivery;

import com.glyph.core.scheduler.SchedulerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Tells players about waiting deliveries when they join (GDD section 23) —
 * sold-while-offline payouts already reached their balance; items wait in
 * the queue.
 */
public final class DeliveryJoinNotifier implements Listener {

    private final DeliveryService deliveries;
    private final SchedulerAdapter scheduler;

    public DeliveryJoinNotifier(DeliveryService deliveries, SchedulerAdapter scheduler) {
        this.deliveries = deliveries;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        deliveries.pendingCount(player.getUniqueId()).thenAccept(pending -> {
            if (pending > 0) {
                scheduler.runForEntity(player, () -> player.sendMessage(Component.text(
                        "You have " + pending + " item(s) waiting — run /claim.",
                        NamedTextColor.GOLD)), null);
            }
        });
    }
}
