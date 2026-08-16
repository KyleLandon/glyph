package com.glyph.core.smp.command;

import com.glyph.api.economy.Money;
import com.glyph.core.claims.GriefPreventionAccess;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.item.ItemCodec;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.smp.shop.ChestShop;
import com.glyph.core.smp.shop.ChestShop.Mode;
import com.glyph.core.smp.shop.ChestShopService;
import com.glyph.core.smp.shop.ChestShopService.CreateStatus;
import com.glyph.core.smp.shop.ChestShopService.DeleteStatus;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /shop sell <price>}, {@code /shop buy <price>}, {@code /shop remove}.
 * Look at a chest on claimed land; hold the item that will be the trade unit.
 */
public final class ShopCommand implements CommandExecutor, TabCompleter {

    private final ChestShopService shops;
    private final EconomySettings money;
    private final SchedulerAdapter scheduler;

    public ShopCommand(
            ChestShopService shops, EconomySettings money, SchedulerAdapter scheduler) {
        this.shops = Objects.requireNonNull(shops, "shops");
        this.money = Objects.requireNonNull(money, "money");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can make shops.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text(
                    "/shop sell <price>  /shop buy <price>  /shop remove",
                    NamedTextColor.GOLD));
            player.sendMessage(Component.text(
                    "Look at a chest. Hold the item. Price is for that stack size.",
                    NamedTextColor.GRAY));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "sell", "buy" -> create(player, sub, args);
            case "remove", "delete" -> remove(player);
            default -> {
                player.sendMessage(Component.text(
                        "Usage: /shop sell <price> | buy <price> | remove", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean create(Player player, String modeName, String[] args) {
        if (args.length != 2) {
            player.sendMessage(Component.text("Usage: /shop " + modeName + " <price>",
                    NamedTextColor.RED));
            return true;
        }
        Money price;
        try {
            price = Money.parse(args[1]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Not a valid price: " + args[1], NamedTextColor.RED));
            return true;
        }
        if (!price.isPositive()) {
            player.sendMessage(Component.text("Price must be at least $1.", NamedTextColor.RED));
            return true;
        }
        Block chest = targetedContainer(player);
        if (chest == null) {
            player.sendMessage(Component.text("Look at a chest within 5 blocks.",
                    NamedTextColor.RED));
            return true;
        }
        if (GriefPreventionAccess.present() && !GriefPreventionAccess.canBuild(player, chest.getLocation())) {
            player.sendMessage(Component.text("Shops go on chests you can build at.",
                    NamedTextColor.RED));
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir() || held.getAmount() < 1) {
            player.sendMessage(Component.text("Hold the item this shop will trade.",
                    NamedTextColor.RED));
            return true;
        }
        ItemStack unit = held.clone();
        Mode mode = modeName.equals("buy") ? Mode.BUY : Mode.SELL;
        ChestShop shop = new ChestShop(
                UUID.randomUUID(),
                player.getUniqueId(),
                "",
                chest.getWorld().getName(),
                chest.getX(),
                chest.getY(),
                chest.getZ(),
                mode,
                price.dollars(),
                ItemCodec.serialize(unit));
        scheduler.runAsync(() -> {
            CreateStatus status = shops.create(shop);
            scheduler.runForEntity(player, () -> player.sendMessage(switch (status) {
                case CREATED -> Component.text(
                        (mode == Mode.SELL ? "Selling " : "Buying ")
                                + unit.getAmount() + " "
                                + unit.getType().name().toLowerCase(Locale.ROOT)
                                + " for " + price.format(money.currencySymbol())
                                + ". Others right-click the chest.",
                        NamedTextColor.GREEN);
                case EXISTS -> Component.text("That chest already has a shop. /shop remove",
                        NamedTextColor.RED);
                case DATABASE_DOWN -> Component.text("Shops are unavailable right now.",
                        NamedTextColor.RED);
            }), null);
        });
        return true;
    }

    private boolean remove(Player player) {
        Block chest = targetedContainer(player);
        if (chest == null) {
            player.sendMessage(Component.text("Look at a shop chest.", NamedTextColor.RED));
            return true;
        }
        scheduler.runAsync(() -> {
            var found = shops.find(chest);
            if (found.isEmpty()) {
                scheduler.runForEntity(player, () -> player.sendMessage(
                        Component.text("No shop on that chest.", NamedTextColor.RED)), null);
                return;
            }
            if (!found.get().ownerUuid().equals(player.getUniqueId())
                    && !player.hasPermission("glyph.admin")) {
                scheduler.runForEntity(player, () -> player.sendMessage(
                        Component.text("That is not your shop.", NamedTextColor.RED)), null);
                return;
            }
            DeleteStatus status = shops.delete(found.get().id(), found.get().ownerUuid());
            scheduler.runForEntity(player, () -> player.sendMessage(switch (status) {
                case DELETED -> Component.text("Shop removed.", NamedTextColor.GREEN);
                case MISSING -> Component.text("Shop already gone.", NamedTextColor.RED);
                case DATABASE_DOWN -> Component.text("Shops are unavailable right now.",
                        NamedTextColor.RED);
            }), null);
        });
        return true;
    }

    static Block targetedContainer(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            return null;
        }
        return target.getState() instanceof Container ? target : null;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("sell", "buy", "remove").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
