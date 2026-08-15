package com.glyph.core.chat;

import com.glyph.core.config.ChatSettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Turns {@code [i]} / {@code [item]} in chat into a hoverable held-item preview
 * (Folia-safe: inventory read + broadcast on the player's region thread).
 */
public final class ItemChatListener implements Listener {

    private final ChatSettings settings;
    private final SchedulerAdapter scheduler;

    public ItemChatListener(ChatSettings settings, SchedulerAdapter scheduler) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!settings.itemPlaceholders()) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!ItemChatFormatter.containsPlaceholder(plain)) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        Set<Audience> viewers = ConcurrentHashMap.newKeySet();
        viewers.addAll(event.viewers());
        String messagePlain = plain;

        scheduler.runForEntity(player, () -> {
            ItemStack hand = player.getInventory().getItemInMainHand().clone();
            Component body = ItemChatFormatter.replacePlaceholders(messagePlain, hand);
            Component chat = Component.translatable(
                    "chat.type.text",
                    player.displayName().colorIfAbsent(NamedTextColor.WHITE),
                    body);
            for (Audience audience : viewers) {
                audience.sendMessage(chat);
            }
        }, null);
    }
}
