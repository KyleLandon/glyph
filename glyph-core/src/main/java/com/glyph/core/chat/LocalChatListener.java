package com.glyph.core.chat;

import com.glyph.core.config.ChatSettings;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Objects;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Default chat is nearby. {@code /g} is the shout. */
public final class LocalChatListener implements Listener {

    private final ChatSettings settings;

    public LocalChatListener(ChatSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!settings.localChat()) {
            return;
        }
        Player speaker = event.getPlayer();
        double range = settings.localRadius();
        event.viewers().removeIf(audience -> !ChatChannels.shouldKeepViewer(audience, speaker, range));
        event.renderer((source, displayName, message, viewer) ->
                ChatChannels.localLine(displayName.colorIfAbsent(NamedTextColor.WHITE), message));
    }
}
