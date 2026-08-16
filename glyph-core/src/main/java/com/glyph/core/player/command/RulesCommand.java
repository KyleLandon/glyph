package com.glyph.core.player.command;

import com.glyph.core.config.ServerRole;
import com.glyph.core.player.RulesBook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /rules} — opens the Glyph rules book for this backend. */
public final class RulesCommand implements CommandExecutor {

    private final ServerRole role;

    public RulesCommand(ServerRole role) {
        this.role = role == null ? ServerRole.ANARCHY : role;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can read the rules book.",
                    NamedTextColor.RED));
            return true;
        }
        player.openBook(role.isSmp() ? RulesBook.createSmp() : RulesBook.create());
        return true;
    }
}
