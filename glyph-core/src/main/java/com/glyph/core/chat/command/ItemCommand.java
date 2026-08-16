package com.glyph.core.chat.command;

import com.glyph.core.chat.ChatChannels;
import com.glyph.core.chat.ItemChatFormatter;
import com.glyph.core.config.ChatSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /item} — show your held item in chat with hover + click-to-copy. */
public final class ItemCommand implements CommandExecutor {

    private final ChatSettings settings;

    public ItemCommand(ChatSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can share items.", NamedTextColor.RED));
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() <= 0) {
            player.sendMessage(Component.text("Hold an item to share it.", NamedTextColor.RED));
            return true;
        }

        Component name = player.displayName().colorIfAbsent(NamedTextColor.WHITE);
        Component body = Component.text("shows ", NamedTextColor.GRAY)
                .append(ItemChatFormatter.itemComponent(hand));
        if (settings.localChat()) {
            ChatChannels.sendLocal(player, name, body, settings.localRadius());
        } else {
            ChatChannels.sendGlobal(player, name, body);
        }
        return true;
    }
}
