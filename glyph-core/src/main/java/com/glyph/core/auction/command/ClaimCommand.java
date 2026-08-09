package com.glyph.core.auction.command;

import com.glyph.core.delivery.DeliveryClaimer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /claim} — hands out pending deliveries (GDD section 23). All the
 * logic lives in {@link DeliveryClaimer}; commands stay thin (GDD 65).
 */
public final class ClaimCommand implements CommandExecutor {

    private final DeliveryClaimer claimer;

    public ClaimCommand(DeliveryClaimer claimer) {
        this.claimer = claimer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can claim deliveries.",
                    NamedTextColor.RED));
            return true;
        }
        claimer.claimAll(player);
        return true;
    }
}
