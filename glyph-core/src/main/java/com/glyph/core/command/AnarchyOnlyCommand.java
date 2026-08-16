package com.glyph.core.command;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** SMP stub: anarchy features stay on the Folia backend. */
public final class AnarchyOnlyCommand implements CommandExecutor {

    private final String feature;

    public AnarchyOnlyCommand(String feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(Component.text(
                feature + " lives on anarchy. Type /server anarchy",
                NamedTextColor.YELLOW));
        return true;
    }
}
