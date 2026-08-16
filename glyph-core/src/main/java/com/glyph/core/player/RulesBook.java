package com.glyph.core.player;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Written rules book given on first join and opened by {@code /rules}.
 */
public final class RulesBook {

    public static final String TITLE = "Glyph Rules";
    public static final String AUTHOR = "Glyph";

    private RulesBook() {
    }

    public static List<Component> pages() {
        return List.of(
                page(
                        title("GLYPH"),
                        Component.text("Anarchy Economy", NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        Component.text("No land claims.", NamedTextColor.BLACK),
                        Component.text("No grief protection.", NamedTextColor.BLACK),
                        Component.text("Staff will not replace", NamedTextColor.BLACK),
                        Component.text("your items.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("Your base can burn.", NamedTextColor.DARK_RED),
                        Component.text("Your items can be stolen.", NamedTextColor.DARK_RED),
                        Component.empty(),
                        Component.text("Trust carefully.", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)),
                page(
                        title("SPAWN"),
                        Component.text("The spawn zone (~96 blocks)", NamedTextColor.BLACK),
                        Component.text("is protected. Do not build", NamedTextColor.BLACK),
                        Component.text("or break there.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("Leave spawn to start.", NamedTextColor.BLACK),
                        Component.text("Danger begins immediately", NamedTextColor.BLACK),
                        Component.text("outside.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("No homes. No warps.", NamedTextColor.DARK_GRAY),
                        Component.text("No free teleports.", NamedTextColor.DARK_GRAY)),
                page(
                        title("MONEY"),
                        Component.text("You start with $100 and", NamedTextColor.BLACK),
                        Component.text("stone tools.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("/bal  /pay  /baltop", NamedTextColor.DARK_BLUE),
                        Component.text("/ah   /ah sell <price>", NamedTextColor.DARK_BLUE),
                        Component.text("/ah mail", NamedTextColor.DARK_BLUE),
                        Component.text("/bounty add <player> $", NamedTextColor.DARK_BLUE),
                        Component.empty(),
                        Component.text("Active play pays about", NamedTextColor.BLACK),
                        Component.text("$25 every 15 minutes.", NamedTextColor.BLACK),
                        Component.text("AFK does not pay.", NamedTextColor.DARK_GRAY)),
                page(
                        title("COMMANDS"),
                        Component.text("/stats  /playtime  /top", NamedTextColor.DARK_BLUE),
                        Component.text("/rules  (this book)", NamedTextColor.DARK_BLUE),
                        Component.empty(),
                        Component.text("Voice chat is optional.", NamedTextColor.BLACK),
                        Component.text("Client pack:", NamedTextColor.BLACK),
                        Component.text("github.com/KyleLandon/", NamedTextColor.DARK_GRAY),
                        Component.text("glyph-clientmods", NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        Component.text("glyphmc.net", NamedTextColor.DARK_BLUE),
                        Component.text("discord.gg/htkQHR4gdf", NamedTextColor.DARK_BLUE)),
                page(
                        title("CHEATS"),
                        Component.text("Allowed: performance mods,", NamedTextColor.BLACK),
                        Component.text("shaders, minimap.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("Banned: killaura, fly,", NamedTextColor.DARK_RED),
                        Component.text("reach, speed, dupes, bots,", NamedTextColor.DARK_RED),
                        Component.text("crashers, inventory hacks.", NamedTextColor.DARK_RED),
                        Component.empty(),
                        Component.text("Play fair. Do not attack", NamedTextColor.BLACK),
                        Component.text("the server.", NamedTextColor.BLACK)),
                page(
                        title("SURVIVE"),
                        Component.text("Walk away from spawn.", NamedTextColor.BLACK),
                        Component.text("Hide a base. Trust slowly.", NamedTextColor.BLACK),
                        Component.text("Sell loot on /ah.", NamedTextColor.BLACK),
                        Component.text("Hunt bounties. Get paid.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("This book stays in your", NamedTextColor.BLACK),
                        Component.text("inventory. Type /rules", NamedTextColor.BLACK),
                        Component.text("anytime to read it again.", NamedTextColor.BLACK)));
    }

    public static ItemStack create() {
        return book(pages());
    }

    public static List<Component> smpPages() {
        return List.of(
                page(
                        title("FOREVER WORLD"),
                        Component.text("This world stays.", NamedTextColor.BLACK),
                        Component.text("Hang out. Build. Stay.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("Golden shovel claims land.", NamedTextColor.BLACK),
                        Component.text("Stick inspects a claim.", NamedTextColor.BLACK),
                        Component.text("/sethome  /nickname  /me", NamedTextColor.DARK_BLUE),
                        Component.text("Active play pays $5 / 15 min.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("Your $ and Glyphs are", NamedTextColor.DARK_GREEN),
                        Component.text("the same wallet as anarchy.", NamedTextColor.DARK_GREEN),
                        Component.text("Inventory is not.", NamedTextColor.BLACK),
                        Component.empty(),
                        Component.text("/server anarchy", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                        Component.text("switches worlds.", NamedTextColor.BLACK)));
    }

    public static ItemStack createSmp() {
        return book(smpPages());
    }

    private static ItemStack book(List<Component> bookPages) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("WRITTEN_BOOK produced no BookMeta");
        }
        meta.title(Component.text(TITLE));
        meta.author(Component.text(AUTHOR));
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.pages(bookPages);
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
