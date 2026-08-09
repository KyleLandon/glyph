package com.glyph.core.delivery;

import com.glyph.core.item.ItemCodec;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Hands pending deliveries to a player (GDD sections 22-23), shared by
 * {@code /claim} and the post-purchase auto-claim.
 *
 * <p>Protocol: count free inventory slots on the entity thread, claim that
 * many rows in the database, then give the items back on the entity thread.
 * If the player vanishes between claim and handover, the rows revert to
 * PENDING — the item is never given before the database committed, and never
 * lost if the handover cannot happen.</p>
 */
public final class DeliveryClaimer {

    private final DeliveryService deliveries;
    private final SchedulerAdapter scheduler;
    private final Logger logger;

    /** One claim in flight per player; blocks double-click duplication. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public DeliveryClaimer(DeliveryService deliveries, SchedulerAdapter scheduler, Logger logger) {
        this.deliveries = deliveries;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    /** Must be called on {@code player}'s entity thread. */
    public void claimAll(Player player) {
        UUID uuid = player.getUniqueId();
        int freeSlots = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.isEmpty()) {
                freeSlots++;
            }
        }
        if (freeSlots == 0) {
            deliveries.pendingCount(uuid).thenAccept(pending -> {
                if (pending > 0) {
                    message(player, Component.text(
                            "Your inventory is full — free up space and run /claim.",
                            NamedTextColor.RED));
                }
            });
            return;
        }
        if (!inFlight.add(uuid)) {
            return;
        }

        deliveries.claim(uuid, freeSlots).whenComplete((claimed, error) -> {
            if (error != null || claimed == null || claimed.isEmpty()) {
                inFlight.remove(uuid);
                if (error == null && (claimed == null || claimed.isEmpty())) {
                    message(player, Component.text("No deliveries waiting.",
                            NamedTextColor.GRAY));
                }
                return;
            }
            List<UUID> ids = claimed.stream().map(Delivery::id).toList();
            scheduler.runForEntity(player, () -> {
                try {
                    deliver(player, claimed);
                } finally {
                    inFlight.remove(uuid);
                }
            }, () -> {
                // Player disconnected before handover: items back to PENDING.
                inFlight.remove(uuid);
                deliveries.revert(ids);
            });
        });
    }

    private void deliver(Player player, List<Delivery> claimed) {
        int given = 0;
        for (Delivery delivery : claimed) {
            try {
                ItemStack item = ItemCodec.deserialize(delivery.payload());
                var leftover = player.getInventory().addItem(item);
                // Free-slot counting happens before the claim, so leftovers are
                // rare (stack-merge edge cases); drop at the player, never delete.
                leftover.values().forEach(rest ->
                        player.getWorld().dropItemNaturally(player.getLocation(), rest));
                given++;
            } catch (Exception e) {
                logger.error("Failed to deliver {} to {} — payload kept CLAIMED for staff review",
                        delivery.id(), player.getName(), e);
            }
        }
        int total = given;
        deliveries.pendingCount(player.getUniqueId()).thenAccept(remaining -> {
            Component message = Component.text("Claimed " + total + " item(s).",
                    NamedTextColor.GREEN);
            if (remaining > 0) {
                message = message.append(Component.text(
                        " " + remaining + " more waiting — run /claim again.",
                        NamedTextColor.GRAY));
            }
            message(player, message);
        });
    }

    private void message(Player player, Component component) {
        scheduler.runForEntity(player, () -> player.sendMessage(component), null);
    }
}
