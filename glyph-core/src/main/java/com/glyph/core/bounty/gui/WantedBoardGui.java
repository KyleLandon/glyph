package com.glyph.core.bounty.gui;

import com.glyph.api.economy.Money;
import com.glyph.core.bounty.BountyRepository.TargetTotal;
import com.glyph.core.bounty.BountyService;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.slf4j.Logger;

/**
 * Chest GUI wanted board — player heads, bounty amounts, dead-or-alive copy.
 *
 * <p>Folia: fetch targets async, open on the viewer's entity thread. Clicks
 * are cancelled; these heads are never real inventory items.</p>
 */
public final class WantedBoardGui implements Listener {

    private static final int SIZE = 27;
    private static final int[] POSTER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int SLOT_INFO = 22;

    private final BountyService bounties;
    private final SchedulerAdapter scheduler;
    private final EconomySettings economy;
    private final Logger logger;

    public WantedBoardGui(BountyService bounties, SchedulerAdapter scheduler,
                          EconomySettings economy, Logger logger) {
        this.bounties = bounties;
        this.scheduler = scheduler;
        this.economy = economy;
        this.logger = logger;
    }

    public void open(Player player) {
        bounties.topTargets(POSTER_SLOTS.length).whenComplete((top, error) -> {
            if (error != null) {
                logger.error("Wanted board failed for {}", player.getName(), error);
                scheduler.runForEntity(player, () -> player.sendMessage(Component.text(
                        "Bounties are unavailable right now.", NamedTextColor.RED)), null);
                return;
            }
            scheduler.runForEntity(player, () -> openBoard(player, top), null);
        });
    }

    private void openBoard(Player player, List<TargetTotal> top) {
        BoardHolder holder = new BoardHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("WANTED")
                .color(NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD));
        holder.inventory = inventory;

        ItemStack frame = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, frame);
        }

        if (top.isEmpty()) {
            inventory.setItem(13, button(Material.PAPER, "No active bounties", NamedTextColor.GRAY,
                    List.of(
                            line("Place one with", NamedTextColor.DARK_GRAY),
                            line("/bounty add <player> <amount>", NamedTextColor.GOLD))));
        } else {
            for (int i = 0; i < top.size() && i < POSTER_SLOTS.length; i++) {
                TargetTotal target = top.get(i);
                int slot = POSTER_SLOTS[i];
                inventory.setItem(slot, posterHead(target, i + 1));
                holder.slots.put(slot, target);
            }
        }

        inventory.setItem(4, button(Material.FILLED_MAP, "WANTED", NamedTextColor.DARK_RED,
                List.of(line("DEAD OR ALIVE", NamedTextColor.GOLD))));
        inventory.setItem(SLOT_INFO, button(Material.WRITABLE_BOOK, "Raise a bounty",
                NamedTextColor.GOLD,
                List.of(
                        line("/bounty add <player> <amount>", NamedTextColor.GRAY),
                        line("Minimum "
                                + Money.of(bounties.settings().minimum())
                                .format(economy.currencySymbol()), NamedTextColor.DARK_GRAY),
                        line("/bounty poster  — take a paper copy", NamedTextColor.DARK_GRAY))));

        player.openInventory(inventory);
    }

    private ItemStack posterHead(TargetTotal target, int rank) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setPlayerProfile(Bukkit.createProfile(target.targetUuid(), target.targetName()));
        meta.displayName(Component.text("#" + rank + "  " + target.targetName(),
                        NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        String amount = Money.of(target.total()).format(economy.currencySymbol());
        String contracts = target.count() == 1 ? "1 open contract" : target.count() + " open contracts";
        meta.lore(List.of(
                line("WANTED", NamedTextColor.DARK_RED),
                line("DEAD OR ALIVE", NamedTextColor.GOLD),
                Component.empty(),
                line(amount, NamedTextColor.YELLOW),
                line(contracts, NamedTextColor.DARK_GRAY),
                Component.empty(),
                line("Click for details", NamedTextColor.GRAY)));
        head.setItemMeta(meta);
        return head;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof BoardHolder board)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        TargetTotal target = board.slots.get(event.getRawSlot());
        if (target == null) {
            return;
        }
        player.closeInventory();
        String amount = Money.of(target.total()).format(economy.currencySymbol());
        player.sendMessage(Component.text()
                .append(Component.text("WANTED  ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.text(target.targetName(), NamedTextColor.RED))
                .build());
        player.sendMessage(Component.text("DEAD OR ALIVE  ", NamedTextColor.GOLD)
                .append(Component.text(amount, NamedTextColor.YELLOW))
                .append(Component.text("  (" + target.count() + ")", NamedTextColor.DARK_GRAY)));
        player.sendMessage(Component.text("Raise it: /bounty add " + target.targetName() + " <amount>",
                NamedTextColor.GRAY));
        player.sendMessage(Component.text("Paper copy: /bounty poster " + target.targetName(),
                NamedTextColor.DARK_GRAY));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BoardHolder) {
            event.setCancelled(true);
        }
    }

    private static final class BoardHolder implements InventoryHolder {
        private final Map<Integer, TargetTotal> slots = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, String label, NamedTextColor color,
                                    List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> decorated = new ArrayList<>(lore.size());
        for (Component line : lore) {
            decorated.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(decorated);
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
