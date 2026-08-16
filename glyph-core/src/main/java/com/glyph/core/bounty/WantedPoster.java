package com.glyph.core.bounty;

import com.glyph.api.economy.Money;
import com.glyph.core.bounty.BountyRepository.TargetTotal;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * One Piece-style wanted posters: written books you can carry, drop, or
 * put in an item frame. Copy stays short (GDD section 32).
 */
public final class WantedPoster {

    public static final String LIST_TITLE = "WANTED";
    public static final String AUTHOR = "Glyph";

    private WantedPoster() {
    }

    public static String posterTitle(String targetName) {
        return "WANTED: " + targetName;
    }

    public static List<Component> posterPages(String targetName, long total, int count,
                                              String symbol) {
        String amount = Money.of(total).format(symbol);
        String contracts = count == 1 ? "1 open contract" : count + " open contracts";
        return List.of(page(
                title("WANTED"),
                Component.empty(),
                Component.text(targetName, NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.empty(),
                Component.text("DEAD OR ALIVE", NamedTextColor.BLACK, TextDecoration.BOLD),
                Component.empty(),
                Component.text(amount, NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.empty(),
                Component.text(contracts, NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("Kill them. Collect.", NamedTextColor.BLACK),
                Component.text("/bounty add to raise it.", NamedTextColor.DARK_GRAY)));
    }

    public static List<Component> listPages(List<TargetTotal> targets, String symbol) {
        if (targets.isEmpty()) {
            return List.of(page(
                    title("WANTED"),
                    Component.empty(),
                    Component.text("No active bounties.", NamedTextColor.DARK_GRAY),
                    Component.empty(),
                    Component.text("/bounty add <player>", NamedTextColor.DARK_BLUE),
                    Component.text("<amount>", NamedTextColor.DARK_BLUE),
                    Component.empty(),
                    Component.text("Minimum $100.", NamedTextColor.BLACK)));
        }
        List<Component> pages = new ArrayList<>();
        int perPage = 6;
        for (int start = 0; start < targets.size(); start += perPage) {
            int end = Math.min(start + perPage, targets.size());
            List<Component> lines = new ArrayList<>();
            lines.add(title("WANTED"));
            lines.add(Component.text("DEAD OR ALIVE", NamedTextColor.DARK_GRAY));
            lines.add(Component.empty());
            for (int i = start; i < end; i++) {
                TargetTotal target = targets.get(i);
                lines.add(Component.text((i + 1) + ". " + target.targetName(),
                        NamedTextColor.DARK_RED, TextDecoration.BOLD));
                lines.add(Component.text("   " + Money.of(target.total()).format(symbol)
                                + "  (" + target.count() + ")",
                        NamedTextColor.BLACK));
            }
            pages.add(page(lines.toArray(Component[]::new)));
        }
        return pages;
    }

    public static ItemStack posterBook(String targetName, long total, int count, String symbol) {
        return book(posterTitle(targetName), posterPages(targetName, total, count, symbol));
    }

    public static ItemStack listBook(List<TargetTotal> targets, String symbol) {
        return book(LIST_TITLE, listPages(targets, symbol));
    }

    private static ItemStack book(String title, List<Component> pages) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("WRITTEN_BOOK produced no BookMeta");
        }
        meta.title(Component.text(title));
        meta.author(Component.text(AUTHOR));
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.pages(pages);
        item.setItemMeta(meta);
        return item;
    }

    private static Component title(String text) {
        return Component.text(text, NamedTextColor.DARK_RED, TextDecoration.BOLD);
    }

    private static Component page(Component... lines) {
        Component page = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                page = page.append(Component.newline());
            }
            page = page.append(lines[i]);
        }
        return page;
    }
}
