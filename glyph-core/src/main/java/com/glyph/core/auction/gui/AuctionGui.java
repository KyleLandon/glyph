package com.glyph.core.auction.gui;

import com.glyph.api.economy.Money;
import com.glyph.core.auction.AuctionListing;
import com.glyph.core.auction.AuctionRepository.BrowsePage;
import com.glyph.core.auction.AuctionRepository.BrowseQuery;
import com.glyph.core.auction.AuctionRepository.Sort;
import com.glyph.core.auction.AuctionService;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.delivery.DeliveryClaimer;
import com.glyph.core.item.ItemCodec;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.slf4j.Logger;

/**
 * Inventory-backed auction house GUI (GDD section 21): browse, search,
 * categories, sort, buy, list, cancel own listings.
 *
 * <p>Folia threading: inventories are created and opened on the player's
 * entity thread; listing pages are fetched on the async executor; every
 * click is cancelled (items in this GUI are never real inventory items).</p>
 */
public final class AuctionGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_MINE = 46;
    private static final int SLOT_CATEGORY = 47;
    private static final int SLOT_CLEAR_SEARCH = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_SORT = 51;
    private static final int SLOT_NEXT = 53;

    private final AuctionService auctions;
    private final DeliveryClaimer claimer;
    private final SchedulerAdapter scheduler;
    private final EconomySettings economy;
    private final Logger logger;

    public AuctionGui(AuctionService auctions, DeliveryClaimer claimer,
                      SchedulerAdapter scheduler, EconomySettings economy, Logger logger) {
        this.auctions = auctions;
        this.claimer = claimer;
        this.scheduler = scheduler;
        this.economy = economy;
        this.logger = logger;
    }

    /** Mutable view state carried between page fetches. */
    public static final class ViewState {
        private int page;
        private Sort sort = Sort.NEWEST;
        private ItemCodec.Category category;
        private String search;
        private boolean mineOnly;

        public static ViewState fresh() {
            return new ViewState();
        }

        public static ViewState search(String text) {
            ViewState state = new ViewState();
            state.search = text;
            return state;
        }
    }

    /** Marker holder: identifies our inventories and carries click context. */
    private static final class BrowseHolder implements InventoryHolder {
        private final ViewState state;
        private final Map<Integer, AuctionListing> slots = new HashMap<>();
        private int pageCount = 1;
        private Inventory inventory;

        private BrowseHolder(ViewState state) {
            this.state = state;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ConfirmHolder implements InventoryHolder {
        private final AuctionListing listing;
        private final ViewState returnState;
        private final boolean cancelIntent;
        private Inventory inventory;

        private ConfirmHolder(AuctionListing listing, ViewState returnState, boolean cancelIntent) {
            this.listing = listing;
            this.returnState = returnState;
            this.cancelIntent = cancelIntent;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Fetches a page async, then builds and opens the GUI on the entity thread. */
    public void open(Player player, ViewState state) {
        BrowseQuery query = new BrowseQuery(
                state.page, PAGE_SIZE, state.sort,
                state.category == null ? null : state.category.name(),
                state.search,
                state.mineOnly ? player.getUniqueId() : null);
        auctions.browse(query).whenComplete((pageResult, error) -> {
            if (error != null) {
                logger.error("Auction browse failed for {}", player.getName(), error);
                scheduler.runForEntity(player, () -> player.sendMessage(Component.text(
                        "Auction house is unavailable right now.", NamedTextColor.RED)), null);
                return;
            }
            // Clamp page when the last item of a page sold mid-view.
            if (pageResult.listings().isEmpty() && state.page > 0) {
                state.page = Math.min(state.page - 1, pageResult.pageCount() - 1);
                open(player, state);
                return;
            }
            scheduler.runForEntity(player, () -> openBrowse(player, state, pageResult), null);
        });
    }

    private void openBrowse(Player player, ViewState state, BrowsePage page) {
        BrowseHolder holder = new BrowseHolder(state);
        holder.pageCount = page.pageCount();
        String title = state.mineOnly ? "Auction House — My Listings" : "Auction House";
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(title));
        holder.inventory = inventory;

        int slot = 0;
        for (AuctionListing listing : page.listings()) {
            inventory.setItem(slot, displayItem(listing, player));
            holder.slots.put(slot, listing);
            slot++;
        }

        if (state.page > 0) {
            inventory.setItem(SLOT_PREV, button(Material.ARROW,
                    "Previous page", NamedTextColor.YELLOW));
        }
        if (state.page < page.pageCount() - 1) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW,
                    "Next page", NamedTextColor.YELLOW));
        }
        inventory.setItem(SLOT_MINE, button(Material.CHEST,
                state.mineOnly ? "Show all listings" : "My listings", NamedTextColor.AQUA));
        inventory.setItem(SLOT_CATEGORY, button(Material.HOPPER,
                "Category: " + (state.category == null ? "All" : state.category.displayName()),
                NamedTextColor.AQUA));
        inventory.setItem(SLOT_SORT, button(Material.COMPARATOR,
                "Sort: " + sortName(state.sort), NamedTextColor.AQUA));
        inventory.setItem(SLOT_PAGE_INFO, button(Material.PAPER,
                "Page " + (state.page + 1) + "/" + page.pageCount()
                        + " — " + page.totalCount() + " listing(s)", NamedTextColor.WHITE));
        if (state.search != null) {
            inventory.setItem(SLOT_CLEAR_SEARCH, button(Material.BARRIER,
                    "Clear search: \"" + state.search + "\"", NamedTextColor.RED));
        }

        player.openInventory(inventory);
    }

    private ItemStack displayItem(AuctionListing listing, Player viewer) {
        ItemStack display;
        try {
            display = ItemCodec.deserialize(listing.itemData());
        } catch (Exception e) {
            display = new ItemStack(Material.BARRIER);
        }
        ItemCodec.ItemSummary summary = ItemCodec.ItemSummary.fromJson(listing.summaryJson());
        boolean own = listing.sellerUuid().equals(viewer.getUniqueId());

        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null
                ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(line("Price: ", Money.of(listing.price())
                .format(economy.currencySymbol()), NamedTextColor.GOLD));
        lore.add(line("Seller: ", summary.sellerName(), NamedTextColor.GRAY));
        lore.add(line("Expires in: ", timeLeft(listing.expiresAt()), NamedTextColor.GRAY));
        lore.add(own
                ? line("", "Click to cancel this listing", NamedTextColor.RED)
                : line("", "Click to buy", NamedTextColor.GREEN));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private void openConfirm(Player player, AuctionListing listing, ViewState returnState,
                             boolean cancelIntent) {
        ConfirmHolder holder = new ConfirmHolder(listing, returnState, cancelIntent);
        String title = cancelIntent ? "Cancel listing?" : "Confirm purchase";
        Inventory inventory = Bukkit.createInventory(holder, 27, Component.text(title));
        holder.inventory = inventory;

        ItemStack item;
        try {
            item = ItemCodec.deserialize(listing.itemData());
        } catch (Exception e) {
            item = new ItemStack(Material.BARRIER);
        }
        inventory.setItem(13, item);
        String price = Money.of(listing.price()).format(economy.currencySymbol());
        inventory.setItem(11, button(Material.LIME_WOOL,
                cancelIntent ? "Confirm cancel" : "Buy for " + price, NamedTextColor.GREEN));
        inventory.setItem(15, button(Material.RED_WOOL, "Back", NamedTextColor.RED));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof BrowseHolder browse) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            handleBrowseClick(event, browse);
        } else if (holder instanceof ConfirmHolder confirm) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            handleConfirmClick(event, confirm);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof BrowseHolder || holder instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
    }

    private void handleBrowseClick(InventoryClickEvent event, BrowseHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ViewState state = holder.state;
        int slot = event.getRawSlot();

        AuctionListing listing = holder.slots.get(slot);
        if (listing != null) {
            boolean own = listing.sellerUuid().equals(player.getUniqueId());
            openConfirm(player, listing, state, own);
            return;
        }
        switch (slot) {
            case SLOT_PREV -> {
                if (state.page > 0) {
                    state.page--;
                    open(player, state);
                }
            }
            case SLOT_NEXT -> {
                if (state.page < holder.pageCount - 1) {
                    state.page++;
                    open(player, state);
                }
            }
            case SLOT_MINE -> {
                state.mineOnly = !state.mineOnly;
                state.page = 0;
                open(player, state);
            }
            case SLOT_CATEGORY -> {
                state.category = nextCategory(state.category);
                state.page = 0;
                open(player, state);
            }
            case SLOT_SORT -> {
                state.sort = Sort.values()[(state.sort.ordinal() + 1) % Sort.values().length];
                state.page = 0;
                open(player, state);
            }
            case SLOT_CLEAR_SEARCH -> {
                if (state.search != null) {
                    state.search = null;
                    state.page = 0;
                    open(player, state);
                }
            }
            default -> { }
        }
    }

    private void handleConfirmClick(InventoryClickEvent event, ConfirmHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 15) {
            open(player, holder.returnState);
            return;
        }
        if (slot != 11) {
            return;
        }
        player.closeInventory();
        if (holder.cancelIntent) {
            auctions.cancel(holder.listing.id(), player.getUniqueId())
                    .whenComplete((status, error) -> scheduler.runForEntity(player, () -> {
                        Component message = switch (status == null ? null : status) {
                            case SUCCESS -> Component.text(
                                    "Listing cancelled — item returned via /claim.",
                                    NamedTextColor.GREEN);
                            case NO_LONGER_ACTIVE -> Component.text(
                                    "That listing already sold or expired.", NamedTextColor.RED);
                            case NOT_FOUND, NOT_OWNER -> Component.text(
                                    "That listing no longer exists.", NamedTextColor.RED);
                            case null -> Component.text(
                                    "Cancel failed — try again later.", NamedTextColor.RED);
                        };
                        player.sendMessage(message);
                        if (status == com.glyph.core.auction.AuctionRepository
                                .CancelStatus.SUCCESS) {
                            claimer.claimAll(player);
                        }
                    }, null));
            return;
        }

        String price = Money.of(holder.listing.price())
                .format(economy.currencySymbol());
        auctions.purchase(holder.listing.id(), player.getUniqueId())
                .whenComplete((result, error) -> scheduler.runForEntity(player, () -> {
                    if (error != null || result == null) {
                        player.sendMessage(Component.text(
                                "Purchase failed — try again later.", NamedTextColor.RED));
                        return;
                    }
                    Component message = switch (result.status()) {
                        case SUCCESS -> Component.text("Bought listing for " + price + ".",
                                NamedTextColor.GREEN);
                        case INSUFFICIENT_FUNDS -> Component.text(
                                "You cannot afford " + price + ".", NamedTextColor.RED);
                        case NO_LONGER_ACTIVE -> Component.text(
                                "Too late — that listing already sold or expired.",
                                NamedTextColor.RED);
                        case SELF_PURCHASE -> Component.text(
                                "You cannot buy your own listing.", NamedTextColor.RED);
                        case NOT_FOUND, ACCOUNT_NOT_FOUND -> Component.text(
                                "That listing no longer exists.", NamedTextColor.RED);
                        case FAILED -> Component.text(
                                "Purchase failed — try again later.", NamedTextColor.RED);
                    };
                    player.sendMessage(message);
                    if (result.status() == com.glyph.core.auction.AuctionRepository
                            .PurchaseStatus.SUCCESS) {
                        claimer.claimAll(player);
                    }
                }, null));
    }

    private static ItemCodec.Category nextCategory(ItemCodec.Category current) {
        ItemCodec.Category[] all = ItemCodec.Category.values();
        if (current == null) {
            return all[0];
        }
        int next = current.ordinal() + 1;
        return next >= all.length ? null : all[next];
    }

    private static String sortName(Sort sort) {
        return switch (sort) {
            case NEWEST -> "Newest";
            case PRICE_ASC -> "Price (low to high)";
            case PRICE_DESC -> "Price (high to low)";
        };
    }

    private static ItemStack button(Material material, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String label, String value, NamedTextColor color) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, color))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static String timeLeft(Instant expiresAt) {
        Duration left = Duration.between(Instant.now(), expiresAt);
        if (left.isNegative()) {
            return "expired";
        }
        long hours = left.toHours();
        if (hours >= 24) {
            return (hours / 24) + "d " + (hours % 24) + "h";
        }
        if (hours >= 1) {
            return hours + "h " + left.toMinutesPart() + "m";
        }
        return left.toMinutes() + "m";
    }
}
