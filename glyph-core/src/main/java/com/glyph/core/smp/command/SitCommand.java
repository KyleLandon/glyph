package com.glyph.core.smp.command;

import com.glyph.core.smp.sit.SitService;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SitCommand implements CommandExecutor {

    private final SitService sit;

    public SitCommand(SitService sit) {
        this.sit = Objects.requireNonNull(sit, "sit");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can sit.", NamedTextColor.RED));
            return true;
        }
        sit.sit(player);
        return true;
    }
}
