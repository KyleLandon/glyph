package com.glyph.core.auction.command;

import com.glyph.api.economy.Money;
import com.glyph.core.auction.AuctionRepository.CreateResult;
import com.glyph.core.auction.AuctionService;
import com.glyph.core.auction.gui.AuctionGui;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.delivery.DeliveryClaimer;
import com.glyph.core.delivery.DeliveryService;
import com.glyph.core.item.ItemCodec;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /ah} — auction house entry point (GDD sections 21, 66).
 *
 * <ul>
 *   <li>{@code /ah} — browse GUI</li>
 *   <li>{@code /ah sell <price>} — list the held item</li>
 *   <li>{@code /ah search <text>} — browse filtered by material/name</li>
 *   <li>{@code /ah mail} — collect bought items / returned listings</li>
 * </ul>
 *
 * <p>Listing follows GDD section 22: the item is snapshotted and removed on
 * the entity thread <em>before</em> the async database work; on any failure
 * it is handed straight back.</p>
 */
public final class AhCommand implements CommandExecutor, TabCompleter {

    private final AuctionService auctions;
    private final AuctionGui gui;
    private final DeliveryService deliveries;
    private final DeliveryClaimer claimer;
    private final SchedulerAdapter scheduler;
    private final EconomySettings economy;
    private final EconomyService balances;

    private final Set<UUID> listingInFlight = ConcurrentHashMap.newKeySet();

    public AhCommand(AuctionService auctions, AuctionGui gui, DeliveryService deliveries,
                     DeliveryClaimer claimer, SchedulerAdapter scheduler,
                     EconomySettings economy, EconomyService balances) {
        this.auctions = auctions;
        this.gui = gui;
        this.deliveries = deliveries;
        this.claimer = claimer;
        this.scheduler = scheduler;
        this.economy = economy;
        this.balances = balances;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use the auction house.",
                    NamedTextColor.RED));
            return true;
        }
        if (!auctions.settings().enabled()) {
            player.sendMessage(Component.text("The auction house is disabled.",
                    NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            gui.open(player, AuctionGui.ViewState.fresh());
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sell(player, label, args);
            case "search" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /" + label + " search <text>",
                            NamedTextColor.RED));
                    return true;
                }
                String text = String.join(" ", List.of(args).subList(1, args.length));
                gui.open(player, AuctionGui.ViewState.search(text));
            }
            case "mail" -> claimer.claimAll(player);
            default -> player.sendMessage(Component.text(
                    "Usage: /" + label + " [sell <price> | search <text> | mail]",
                    NamedTextColor.RED));
        }
        return true;
    }

    private void sell(Player player, String label, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /" + label + " sell <price>",
                    NamedTextColor.RED));
            return;
        }
        Money price;
        try {
            price = Money.parse(args[1]);
        } catch (IllegalArgumentException | ArithmeticException e) {
            player.sendMessage(Component.text("Not a valid price: " + args[1],
                    NamedTextColor.RED));
            return;
        }
        if (!price.isPositive()) {
            player.sendMessage(Component.text("Price must be positive.", NamedTextColor.RED));
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            player.sendMessage(Component.text("Hold the item you want to sell.",
                    NamedTextColor.RED));
            return;
        }
        if (!listingInFlight.add(player.getUniqueId())) {
            player.sendMessage(Component.text("Your previous listing is still processing.",
                    NamedTextColor.RED));
            return;
        }

        // Entity thread: snapshot + remove BEFORE any async work (GDD 22).
        ItemStack snapshot = held.clone();
        player.getInventory().setItemInMainHand(null);

        long listingFee = auctions.settings().listingFee(price.dollars());
        auctions.list(player.getUniqueId(), player.getName(), snapshot, price.dollars())
                .whenComplete((result, error) -> {
                    listingInFlight.remove(player.getUniqueId());
                    boolean listed = error == null && result != null
                            && result.status() == com.glyph.core.auction.AuctionRepository
                            .CreateStatus.SUCCESS;
                    scheduler.runForEntity(player,
                            () -> respondToListing(player, snapshot, price, listingFee,
                                    error != null ? null : result),
                            // Player gone before the outcome arrived. If the listing
                            // failed the removed item must not vanish: queue it as a
                            // delivery for their next /ah mail.
                            () -> {
                                if (!listed) {
                                    deliveries.createReturn(player.getUniqueId(),
                                            ItemCodec.serialize(snapshot), "LISTING_FAILED");
                                }
                            });
                });
    }

    private void respondToListing(Player player, ItemStack snapshot, Money price,
                                  long listingFee, CreateResult result) {
        String symbol = economy.currencySymbol();
        if (result != null && result.status() == com.glyph.core.auction.AuctionRepository
                .CreateStatus.SUCCESS) {
            Component message = Component.text("Listed for " + price.format(symbol) + ".",
                    NamedTextColor.GREEN);
            if (listingFee > 0) {
                message = message.append(Component.text(
                        " Listing fee: " + Money.of(listingFee).format(symbol),
                        NamedTextColor.GRAY));
            }
            player.sendMessage(message);
            balances.resyncBalance(player.getUniqueId());
            return;
        }

        // Failure: give the item straight back (same thread as the removal).
        player.getInventory().addItem(snapshot).values().forEach(rest ->
                player.getWorld().dropItemNaturally(player.getLocation(), rest));
        Component message = switch (result == null ? null : result.status()) {
            case INSUFFICIENT_FUNDS -> Component.text("You cannot afford the "
                    + Money.of(listingFee).format(symbol) + " listing fee.",
                    NamedTextColor.RED);
            case LIMIT_REACHED -> Component.text("You already have "
                    + auctions.settings().maxListingsPerPlayer() + " active listings.",
                    NamedTextColor.RED);
            case null, default -> Component.text("Listing failed — item returned.",
                    NamedTextColor.RED);
        };
        player.sendMessage(message);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("sell", "search", "mail").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
