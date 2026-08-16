package com.glyph.core.nick.command;

import com.glyph.core.glyphs.GlyphsService;
import com.glyph.core.hud.TabListDisplay;
import com.glyph.core.nick.NicknameNames;
import com.glyph.core.nick.NicknameService;
import com.glyph.core.nick.NicknameService.ClearStatus;
import com.glyph.core.nick.NicknameService.SetStatus;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Forever World {@code /nickname} — character name in chat and tab. */
public final class NicknameCommand implements CommandExecutor, TabCompleter {

    private final NicknameService nicknames;
    private final GlyphsService glyphs;
    private final TabListDisplay tabList;
    private final SchedulerAdapter scheduler;

    public NicknameCommand(
            NicknameService nicknames,
            GlyphsService glyphs,
            TabListDisplay tabList,
            SchedulerAdapter scheduler) {
        this.nicknames = nicknames;
        this.glyphs = glyphs;
        this.tabList = tabList;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set a nickname.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            String current = nicknames.nickname(player.getUniqueId()).orElse(player.getName());
            player.sendMessage(Component.text("You are ", NamedTextColor.GRAY)
                    .append(Component.text(current, NamedTextColor.WHITE))
                    .append(Component.text(". /nickname <name> or /nickname off", NamedTextColor.DARK_GRAY)));
            return true;
        }
        String raw = String.join(" ", args);
        if (NicknameNames.isClearToken(raw)) {
            scheduler.runAsync(() -> {
                ClearStatus status = nicknames.clear(player.getUniqueId());
                scheduler.runForEntity(player, () -> {
                    refresh(player);
                    player.sendMessage(switch (status) {
                        case CLEARED, NONE -> Component.text(
                                "Nickname cleared. You are " + player.getName() + " again.",
                                NamedTextColor.GREEN);
                        case DATABASE_DOWN -> Component.text(
                                "Nicknames are unavailable right now.", NamedTextColor.RED);
                    });
                }, null);
            });
            return true;
        }
        scheduler.runAsync(() -> {
            SetStatus status = nicknames.set(player.getUniqueId(), raw);
            scheduler.runForEntity(player, () -> {
                if (status == SetStatus.SAVED) {
                    refresh(player);
                }
                player.sendMessage(switch (status) {
                    case SAVED -> Component.text("You are now ", NamedTextColor.GREEN)
                            .append(Component.text(
                                    nicknames.visibleName(player.getUniqueId(), player.getName()),
                                    NamedTextColor.WHITE))
                            .append(Component.text(".", NamedTextColor.GREEN));
                    case BAD_NAME -> Component.text(
                            "Nicknames are 2–16 letters, numbers, spaces, _ or -. No symbols.",
                            NamedTextColor.RED);
                    case TAKEN -> Component.text(
                            "That name is already in use.", NamedTextColor.RED);
                    case DATABASE_DOWN -> Component.text(
                            "Nicknames are unavailable right now.", NamedTextColor.RED);
                });
            }, null);
        });
        return true;
    }

    private void refresh(Player player) {
        glyphs.applyDisplayName(player);
        tabList.refreshName(player.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && "off".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("off");
        }
        return List.of();
    }
}
