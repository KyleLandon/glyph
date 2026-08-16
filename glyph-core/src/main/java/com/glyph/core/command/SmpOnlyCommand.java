package com.glyph.core.command;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Anarchy stub: Forever World features stay on the Paper backend. */
public final class SmpOnlyCommand implements CommandExecutor {

    private final String feature;

    public SmpOnlyCommand(String feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text(
                feature + " live on the Forever World. Type /server smp",
                NamedTextColor.YELLOW));
        return true;
    }
}
