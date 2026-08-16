package com.glyph.core.smp.shop;

import com.glyph.api.economy.Money;
import com.glyph.api.economy.TransferResult;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.item.ItemCodec;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Right-click a shop chest to trade. Owners sneak-click to open it normally.
 */
public final class ShopListener implements Listener {

    private final ChestShopService shops;
    private final EconomyService economy;
    private final EconomySettings money;
    private final SchedulerAdapter scheduler;
    private final Logger logger;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public ShopListener(
            ChestShopService shops,
            EconomyService economy,
            EconomySettings money,
            SchedulerAdapter scheduler,
            Logger logger) {
        this.shops = Objects.requireNonNull(shops, "shops");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.money = Objects.requireNonNull(money, "money");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Container)) {
            return;
        }
        ChestShop shop = shops.cached(block).orElse(null);
        if (shop == null) {
            return;
        }
        Player player = event.getPlayer();
        boolean owner = shop.ownerUuid().equals(player.getUniqueId());
        if (owner && player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        if (owner) {
            player.sendMessage(Component.text(
                    "Your shop. Sneak + right-click to open the chest. /shop remove to delete.",
                    NamedTextColor.GRAY));
            return;
        }
        trade(player, block, shop);
    }

    private void trade(Player customer, Block block, ChestShop shop) {
        if (!inFlight.add(shop.id())) {
            customer.sendMessage(Component.text("Shop is busy. Try again.", NamedTextColor.YELLOW));
            return;
        }
        ItemStack unit;
        try {
            unit = ItemCodec.deserialize(shop.itemData());
        } catch (RuntimeException e) {
            inFlight.remove(shop.id());
            customer.sendMessage(Component.text("This shop's item is broken.", NamedTextColor.RED));
            logger.error("Shop item deserialize failed for {}", shop.id(), e);
            return;
        }
        if (!(block.getState() instanceof Container container)) {
            inFlight.remove(shop.id());
            return;
        }
        Inventory chest = container.getInventory();
        Money price = Money.of(shop.price());
        if (shop.mode() == ChestShop.Mode.SELL) {
            ItemStack taken = takeSimilar(chest, unit);
            if (taken == null) {
                inFlight.remove(shop.id());
                customer.sendMessage(Component.text("Shop is out of stock.", NamedTextColor.RED));
                return;
            }
            economy.transfer(customer.getUniqueId(), shop.ownerUuid(), price, null)
                    .whenComplete((result, error) -> scheduler.runForEntity(customer, () -> {
                        try {
                            if (error != null || result == null || !result.isSuccess()) {
                                chest.addItem(taken);
                                customer.sendMessage(failMessage(result));
                                return;
                            }
                            Map<Integer, ItemStack> leftover = customer.getInventory().addItem(taken);
                            leftover.values().forEach(item ->
                                    customer.getWorld().dropItemNaturally(customer.getLocation(), item));
                            customer.sendMessage(Component.text(
                                    "Bought for " + price.format(money.currencySymbol()) + ".",
                                    NamedTextColor.GREEN));
                        } finally {
                            inFlight.remove(shop.id());
                        }
                    }, () -> {
                        chest.addItem(taken);
                        inFlight.remove(shop.id());
                    }));
            return;
        }
        ItemStack taken = takeSimilar(customer.getInventory(), unit);
        if (taken == null) {
            inFlight.remove(shop.id());
            customer.sendMessage(Component.text(
                    "You need " + unit.getAmount() + " "
                            + unit.getType().name().toLowerCase(Locale.ROOT) + ".",
                    NamedTextColor.RED));
            return;
        }
        Map<Integer, ItemStack> overflow = chest.addItem(taken.clone());
        if (!overflow.isEmpty()) {
            customer.getInventory().addItem(taken);
            overflow.values().forEach(chest::removeItem);
            inFlight.remove(shop.id());
            customer.sendMessage(Component.text("Shop chest is full.", NamedTextColor.RED));
            return;
        }
        economy.transfer(shop.ownerUuid(), customer.getUniqueId(), price, null)
                .whenComplete((result, error) -> scheduler.runForEntity(customer, () -> {
                    try {
                        if (error != null || result == null || !result.isSuccess()) {
                            chest.removeItem(taken);
                            customer.getInventory().addItem(taken);
                            customer.sendMessage(result != null
                                    && result.status() == TransferResult.Status.INSUFFICIENT_FUNDS
                                    ? Component.text("Shop owner cannot pay right now.",
                                            NamedTextColor.RED)
                                    : failMessage(result));
                            return;
                        }
                        customer.sendMessage(Component.text(
                                "Sold for " + price.format(money.currencySymbol()) + ".",
                                NamedTextColor.GREEN));
                    } finally {
                        inFlight.remove(shop.id());
                    }
                }, () -> {
                    chest.removeItem(taken);
                    inFlight.remove(shop.id());
                }));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ChestShop shop = shops.cached(block).orElse(null);
        if (shop == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!shop.ownerUuid().equals(player.getUniqueId()) && !player.hasPermission("glyph.admin")) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That chest is a shop.", NamedTextColor.RED));
            return;
        }
        shops.delete(shop.id(), shop.ownerUuid());
        player.sendMessage(Component.text("Shop removed with the chest.", NamedTextColor.GRAY));
    }

    private Component failMessage(TransferResult result) {
        if (result != null && result.status() == TransferResult.Status.INSUFFICIENT_FUNDS) {
            return Component.text("Not enough money.", NamedTextColor.RED);
        }
        return Component.text("Trade failed.", NamedTextColor.RED);
    }

    static ItemStack takeSimilar(Inventory inventory, ItemStack unit) {
        int needed = unit.getAmount();
        int have = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot != null && slot.isSimilar(unit)) {
                have += slot.getAmount();
            }
        }
        if (have < needed) {
            return null;
        }
        int remaining = needed;
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || !slot.isSimilar(unit)) {
                continue;
            }
            int take = Math.min(remaining, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            if (slot.getAmount() <= 0) {
                inventory.setItem(i, null);
            }
            remaining -= take;
        }
        ItemStack taken = unit.clone();
        taken.setAmount(needed);
        return taken;
    }
}
