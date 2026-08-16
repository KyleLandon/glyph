package com.glyph.core.smp.trade;

import com.glyph.api.economy.Money;
import com.glyph.api.economy.TransferResult;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Two-player trade GUI (GDD section 24). Shared inventory; both must confirm.
 * Changing items or money resets confirmation.
 */
public final class TradeGui implements Listener {

    private static final int SIZE = 54;
    private static final int SLOT_DIVIDER_START = 4;
    private static final int SLOT_A_MONEY = 45;
    private static final int SLOT_A_CONFIRM = 46;
    private static final int SLOT_CANCEL = 49;
    private static final int SLOT_B_CONFIRM = 52;
    private static final int SLOT_B_MONEY = 53;

    private final EconomyService economy;
    private final EconomySettings money;
    private final SchedulerAdapter scheduler;
    private final Map<UUID, Session> byPlayer = new ConcurrentHashMap<>();

    public TradeGui(EconomyService economy, EconomySettings money, SchedulerAdapter scheduler) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.money = Objects.requireNonNull(money, "money");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public boolean isBusy(UUID player) {
        return byPlayer.containsKey(player);
    }

    public void open(Player initiator, Player target) {
        Session session = new Session(initiator.getUniqueId(), target.getUniqueId());
        Inventory inventory = Bukkit.createInventory(session, SIZE, Component.text("Trade"));
        session.inventory = inventory;
        paintChrome(session);
        byPlayer.put(initiator.getUniqueId(), session);
        byPlayer.put(target.getUniqueId(), session);
        initiator.openInventory(inventory);
        target.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Session session)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (session.completing) {
            return;
        }
        int slot = event.getRawSlot();
        boolean initiator = player.getUniqueId().equals(session.initiator);
        if (slot == SLOT_CANCEL) {
            cancel(session, "Trade cancelled.");
            return;
        }
        if (slot == SLOT_A_CONFIRM && initiator || slot == SLOT_B_CONFIRM && !initiator) {
            if (initiator) {
                session.initiatorConfirmed = true;
            } else {
                session.targetConfirmed = true;
            }
            paintChrome(session);
            if (session.initiatorConfirmed && session.targetConfirmed) {
                complete(session);
            }
            return;
        }
        if (slot == SLOT_A_MONEY && initiator || slot == SLOT_B_MONEY && !initiator) {
            int delta = event.getClick() == ClickType.SHIFT_LEFT
                    || event.getClick() == ClickType.SHIFT_RIGHT ? 100 : 10;
            if (event.isRightClick()) {
                if (initiator) {
                    session.initiatorMoney = 0;
                } else {
                    session.targetMoney = 0;
                }
            } else {
                if (initiator) {
                    session.initiatorMoney = Math.min(1_000_000, session.initiatorMoney + delta);
                } else {
                    session.targetMoney = Math.min(1_000_000, session.targetMoney + delta);
                }
            }
            session.initiatorConfirmed = false;
            session.targetConfirmed = false;
            paintChrome(session);
            return;
        }
        if (slot >= SIZE) {
            ItemStack cursor = event.getCurrentItem();
            if (cursor == null || cursor.getType().isAir()) {
                return;
            }
            int dest = firstEmpty(session, initiator);
            if (dest < 0) {
                return;
            }
            session.inventory.setItem(dest, cursor.clone());
            event.getClickedInventory().setItem(event.getSlot(), null);
            session.initiatorConfirmed = false;
            session.targetConfirmed = false;
            paintChrome(session);
            return;
        }
        if (ownsSlot(session, slot, initiator)) {
            ItemStack there = session.inventory.getItem(slot);
            if (there == null || there.getType().isAir()) {
                return;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(there);
            leftover.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            session.inventory.setItem(slot, null);
            session.initiatorConfirmed = false;
            session.targetConfirmed = false;
            paintChrome(session);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Session) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Session session) || session.completing) {
            return;
        }
        cancel(session, "Trade cancelled.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session != null) {
            cancel(session, "Trade cancelled — a player left.");
        }
    }

    private void complete(Session session) {
        session.completing = true;
        Player initiator = Bukkit.getPlayer(session.initiator);
        Player target = Bukkit.getPlayer(session.target);
        if (initiator == null || target == null
                || initiator.getLocation().distanceSquared(target.getLocation()) > 256) {
            session.completing = false;
            cancel(session, "Trade failed — too far or offline.");
            return;
        }
        List<ItemStack> aItems = takeSlots(session, true);
        List<ItemStack> bItems = takeSlots(session, false);
        long aPays = session.initiatorMoney;
        long bPays = session.targetMoney;
        initiator.closeInventory();
        target.closeInventory();
        Runnable give = () -> {
            giveItems(initiator, bItems);
            giveItems(target, aItems);
            initiator.sendMessage(Component.text("Trade complete.", NamedTextColor.GREEN));
            target.sendMessage(Component.text("Trade complete.", NamedTextColor.GREEN));
            forget(session);
        };
        Runnable rollback = () -> {
            giveItems(initiator, aItems);
            giveItems(target, bItems);
            initiator.sendMessage(Component.text("Trade failed. Items returned.", NamedTextColor.RED));
            target.sendMessage(Component.text("Trade failed. Items returned.", NamedTextColor.RED));
            forget(session);
        };
        if (aPays == bPays) {
            give.run();
            return;
        }
        UUID from = aPays > bPays ? session.initiator : session.target;
        UUID to = aPays > bPays ? session.target : session.initiator;
        Money net = Money.of(Math.abs(aPays - bPays));
        economy.transfer(from, to, net, null).whenComplete((result, error) ->
                scheduler.runGlobal(() -> {
                    if (error != null || result == null || !result.isSuccess()) {
                        if (result != null && result.status() == TransferResult.Status.INSUFFICIENT_FUNDS) {
                            Player poor = Bukkit.getPlayer(from);
                            if (poor != null) {
                                poor.sendMessage(Component.text(
                                        "Not enough money for that trade.", NamedTextColor.RED));
                            }
                        }
                        rollback.run();
                    } else {
                        give.run();
                    }
                }));
    }

    private void cancel(Session session, String message) {
        if (session.completing) {
            return;
        }
        session.completing = true;
        Player initiator = Bukkit.getPlayer(session.initiator);
        Player target = Bukkit.getPlayer(session.target);
        List<ItemStack> aItems = takeSlots(session, true);
        List<ItemStack> bItems = takeSlots(session, false);
        if (initiator != null) {
            giveItems(initiator, aItems);
            initiator.closeInventory();
            initiator.sendMessage(Component.text(message, NamedTextColor.YELLOW));
        } else {
            aItems.forEach(item -> { });
        }
        if (target != null) {
            giveItems(target, bItems);
            target.closeInventory();
            target.sendMessage(Component.text(message, NamedTextColor.YELLOW));
        }
        forget(session);
    }

    private void forget(Session session) {
        byPlayer.remove(session.initiator, session);
        byPlayer.remove(session.target, session);
    }

    private void paintChrome(Session session) {
        ItemStack divider = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int row = 0; row < 6; row++) {
            session.inventory.setItem(SLOT_DIVIDER_START + row * 9, divider);
        }
        session.inventory.setItem(SLOT_A_MONEY, moneyIcon(session.initiatorMoney, true));
        session.inventory.setItem(SLOT_B_MONEY, moneyIcon(session.targetMoney, false));
        session.inventory.setItem(SLOT_A_CONFIRM, confirmIcon(session.initiatorConfirmed));
        session.inventory.setItem(SLOT_B_CONFIRM, confirmIcon(session.targetConfirmed));
        session.inventory.setItem(SLOT_CANCEL, named(Material.BARRIER, "Cancel"));
    }

    private ItemStack moneyIcon(long amount, boolean left) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(
                (left ? "You offer " : "They offer ") + Money.of(amount).format(money.currencySymbol()),
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Left-click +$10  Shift +$100", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to reset", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack confirmIcon(boolean confirmed) {
        return named(confirmed ? Material.LIME_DYE : Material.GRAY_DYE,
                confirmed ? "Confirmed" : "Click to confirm");
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean ownsSlot(Session session, int slot, boolean initiator) {
        if (slot < 0 || slot >= 45 || slot % 9 == 4) {
            return false;
        }
        int col = slot % 9;
        return initiator ? col < 4 : col > 4;
    }

    private static int firstEmpty(Session session, boolean initiator) {
        for (int slot = 0; slot < 45; slot++) {
            if (!ownsSlot(session, slot, initiator)) {
                continue;
            }
            ItemStack there = session.inventory.getItem(slot);
            if (there == null || there.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

    private static List<ItemStack> takeSlots(Session session, boolean initiator) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            if (!ownsSlot(session, slot, initiator)) {
                continue;
            }
            ItemStack there = session.inventory.getItem(slot);
            if (there != null && !there.getType().isAir()) {
                items.add(there.clone());
                session.inventory.setItem(slot, null);
            }
        }
        return items;
    }

    private static void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.values().forEach(extra ->
                    player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
    }

    static final class Session implements InventoryHolder {
        private final UUID initiator;
        private final UUID target;
        private Inventory inventory;
        private long initiatorMoney;
        private long targetMoney;
        private boolean initiatorConfirmed;
        private boolean targetConfirmed;
        private boolean completing;

        private Session(UUID initiator, UUID target) {
            this.initiator = initiator;
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
