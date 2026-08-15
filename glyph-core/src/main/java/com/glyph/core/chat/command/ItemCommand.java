package com.glyph.core.chat.command;

import com.glyph.core.chat.ItemChatFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** {@code /item} — show your held item in chat with hover + click-to-copy. */
public final class ItemCommand implements CommandExecutor {

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

        Component message = Component.text()
                .append(player.displayName().colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" shows ", NamedTextColor.GRAY))
                .append(ItemChatFormatter.itemComponent(hand))
                .build();
        Bukkit.getServer().sendMessage(message);
        return true;
    }
}
