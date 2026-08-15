package com.glyph.core.glyphs.command;

import com.glyph.core.economy.command.CommandFeedback;
import com.glyph.core.glyphs.GlyphCatalog;
import com.glyph.core.glyphs.GlyphProduct;
import com.glyph.core.glyphs.GlyphProductType;
import com.glyph.core.glyphs.GlyphShopService;
import com.glyph.core.glyphs.GlyphTitles;
import com.glyph.core.glyphs.GlyphsService;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /glyphs} — balance, shop, buy, color, title, death, unlocks, hud (docs/GLYPHS.md).
 */
public final class GlyphsCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("shop", "buy", "color", "title", "death", "unlocks", "hud");

    private final GlyphsService glyphs;
    private final GlyphShopService shop;
    private final SchedulerAdapter scheduler;

    public GlyphsCommand(GlyphsService glyphs, GlyphShopService shop, SchedulerAdapter scheduler) {
        this.glyphs = glyphs;
        this.shop = shop;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!glyphs.settings().enabled()) {
            sender.sendMessage(Component.text("Glyphs are disabled.", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            showBalance(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "shop" -> {
                showShop(player);
                yield true;
            }
            case "buy" -> {
                if (args.length < 2) {
                    usage(player, label);
                    yield true;
                }
                buy(player, args[1]);
                yield true;
            }
            case "color" -> {
                if (args.length < 2) {
                    usage(player, label);
                    yield true;
                }
                equipColor(player, args[1]);
                yield true;
            }
            case "title" -> {
                if (args.length < 2) {
                    usage(player, label);
                    yield true;
                }
                equipTitle(player, args[1]);
                yield true;
            }
            case "death" -> {
                if (args.length < 2) {
                    usage(player, label);
                    yield true;
                }
                equipDeath(player, args[1]);
                yield true;
            }
            case "unlocks" -> {
                showUnlocks(player);
                yield true;
            }
            case "hud" -> {
                if (args.length < 2) {
                    usage(player, label);
                    yield true;
                }
                setHud(player, args[1]);
                yield true;
            }
            default -> {
                usage(player, label);
                yield true;
            }
        };
    }

    private void showBalance(Player player) {
        UUID uuid = player.getUniqueId();
        glyphs.balance(uuid).whenComplete((balance, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Glyphs unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            long lifetime = glyphs.lifetimeEarned(uuid);
            String symbol = glyphs.settings().symbol();
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Balance: ", NamedTextColor.GRAY)
                    .append(Component.text(symbol + balance, NamedTextColor.LIGHT_PURPLE)));
            lines.add(Component.text("Lifetime earned: ", NamedTextColor.GRAY)
                    .append(Component.text(symbol + lifetime, NamedTextColor.LIGHT_PURPLE)));
            GlyphsService.discordTier(lifetime).ifPresentOrElse(
                    tier -> lines.add(Component.text("Discord tier: ", NamedTextColor.GRAY)
                            .append(Component.text(tier + " (lifetime " + symbol + lifetime + ")",
                                    NamedTextColor.AQUA))),
                    () -> lines.add(Component.text("Discord tier: none", NamedTextColor.DARK_GRAY)));
            lines.add(Component.text("Equipped color: ", NamedTextColor.GRAY)
                    .append(Component.text(formatEquippedColor(uuid), NamedTextColor.WHITE)));
            lines.add(Component.text("Equipped title: ", NamedTextColor.GRAY)
                    .append(Component.text(formatEquippedTitle(uuid), NamedTextColor.WHITE)));
            lines.add(Component.text("Equipped death: ", NamedTextColor.GRAY)
                    .append(Component.text(formatEquippedDeath(uuid), NamedTextColor.WHITE)));
            CommandFeedback.deliver(scheduler, player, lines);
        });
    }

    private String formatEquippedColor(UUID uuid) {
        return glyphs.nameColor(uuid)
                .map(color -> color.toString())
                .orElse("default (white)");
    }

    private String formatEquippedTitle(UUID uuid) {
        return glyphs.equippedTitleText(uuid).orElse("none");
    }

    private String formatEquippedDeath(UUID uuid) {
        return glyphs.deathStyleProductId(uuid)
                .flatMap(GlyphCatalog::find)
                .map(GlyphProduct::displayName)
                .orElse("default");
    }

    private void showShop(Player player) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Glyph shop:", NamedTextColor.LIGHT_PURPLE));
        String symbol = glyphs.settings().symbol();
        List<GlyphProduct> products = GlyphCatalog.all().stream()
                .sorted(Comparator.comparing(GlyphProduct::type).thenComparing(GlyphProduct::cost))
                .toList();
        for (GlyphProduct product : products) {
            lines.add(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(product.id(), NamedTextColor.WHITE))
                    .append(Component.text(" — " + product.displayName() + " (",
                            NamedTextColor.GRAY))
                    .append(Component.text(symbol + product.cost(), NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(")", NamedTextColor.GRAY)));
        }
        lines.add(Component.text("Use /glyphs buy <id>", NamedTextColor.DARK_GRAY));
        CommandFeedback.deliver(scheduler, player, lines);
    }

    private void buy(Player player, String productId) {
        UUID actor = player.getUniqueId();
        shop.buy(actor, productId, actor).whenComplete((outcome, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Purchase failed — try again later.", NamedTextColor.RED));
                return;
            }
            Component message = switch (outcome.status()) {
                case SUCCESS -> Component.text("Purchased ", NamedTextColor.GREEN)
                        .append(Component.text(outcome.product().displayName(), NamedTextColor.WHITE))
                        .append(Component.text("!", NamedTextColor.GREEN));
                case INSUFFICIENT -> Component.text("Not enough Glyphs.", NamedTextColor.RED);
                case ALREADY_OWNED -> Component.text("You already own that.", NamedTextColor.RED);
                case UNKNOWN_PRODUCT -> Component.text("Unknown product: " + productId,
                        NamedTextColor.RED);
                case DISABLED -> Component.text("Glyphs are disabled.", NamedTextColor.RED);
                default -> Component.text("Purchase failed — try again later.", NamedTextColor.RED);
            };
            CommandFeedback.deliver(scheduler, player, message);
        });
    }

    private void equipColor(Player player, String productIdOrNone) {
        glyphs.equipColor(player.getUniqueId(), productIdOrNone).whenComplete((result, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Could not update color — try again later.", NamedTextColor.RED));
                return;
            }
            Component message = switch (result) {
                case EQUIPPED -> Component.text("Name color equipped.", NamedTextColor.GREEN);
                case CLEARED -> Component.text("Name color cleared.", NamedTextColor.GREEN);
                case NOT_UNLOCKED -> Component.text("You have not unlocked that color.",
                        NamedTextColor.RED);
                case UNKNOWN -> Component.text("Unknown color: " + productIdOrNone,
                        NamedTextColor.RED);
                default -> Component.text("Could not update color.", NamedTextColor.RED);
            };
            CommandFeedback.deliver(scheduler, player, message);
        });
    }

    private void equipTitle(Player player, String productIdOrNone) {
        glyphs.equipTitle(player.getUniqueId(), productIdOrNone).whenComplete((result, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Could not update title — try again later.", NamedTextColor.RED));
                return;
            }
            Component message = switch (result) {
                case EQUIPPED -> Component.text("Title equipped.", NamedTextColor.GREEN);
                case CLEARED -> Component.text("Title cleared.", NamedTextColor.GREEN);
                case NOT_UNLOCKED -> Component.text("You have not unlocked that title.",
                        NamedTextColor.RED);
                case UNKNOWN -> Component.text("Unknown title: " + productIdOrNone,
                        NamedTextColor.RED);
                default -> Component.text("Could not update title.", NamedTextColor.RED);
            };
            CommandFeedback.deliver(scheduler, player, message);
        });
    }

    private void equipDeath(Player player, String productIdOrNone) {
        glyphs.equipDeathStyle(player.getUniqueId(), productIdOrNone).whenComplete((result, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Could not update death style — try again later.", NamedTextColor.RED));
                return;
            }
            Component message = switch (result) {
                case EQUIPPED -> Component.text("Death style equipped.", NamedTextColor.GREEN);
                case CLEARED -> Component.text("Death style cleared.", NamedTextColor.GREEN);
                case NOT_UNLOCKED -> Component.text("You have not unlocked that death style.",
                        NamedTextColor.RED);
                case UNKNOWN -> Component.text("Unknown death style: " + productIdOrNone,
                        NamedTextColor.RED);
                default -> Component.text("Could not update death style.", NamedTextColor.RED);
            };
            CommandFeedback.deliver(scheduler, player, message);
        });
    }

    private void setHud(Player player, String mode) {
        String lower = mode.toLowerCase(Locale.ROOT);
        if (!List.of("on", "true", "yes", "off", "false", "no").contains(lower)) {
            usage(player, "glyphs");
            return;
        }
        boolean enabled = lower.equals("on") || lower.equals("true") || lower.equals("yes");
        glyphs.setHudEnabled(player.getUniqueId(), enabled).whenComplete((result, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Could not update HUD — try again later.", NamedTextColor.RED));
                return;
            }
            Component message = enabled
                    ? Component.text("Glyph HUD enabled.", NamedTextColor.GREEN)
                    : Component.text("Glyph HUD disabled.", NamedTextColor.GREEN);
            CommandFeedback.deliver(scheduler, player, message);
        });
    }

    private void showUnlocks(Player player) {
        glyphs.unlocks(player.getUniqueId()).whenComplete((unlocks, error) -> {
            if (error != null) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "Unlocks unavailable — try again later.", NamedTextColor.RED));
                return;
            }
            if (unlocks.isEmpty()) {
                CommandFeedback.deliver(scheduler, player, Component.text(
                        "You have no Glyph unlocks yet.", NamedTextColor.GRAY));
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("Your unlocks:", NamedTextColor.LIGHT_PURPLE));
            for (String id : unlocks) {
                GlyphCatalog.find(id).ifPresentOrElse(
                        product -> lines.add(Component.text(" • ", NamedTextColor.DARK_GRAY)
                                .append(Component.text(id, NamedTextColor.WHITE))
                                .append(Component.text(" — " + product.displayName(),
                                        NamedTextColor.GRAY))),
                        () -> GlyphTitles.displayText(id).ifPresentOrElse(
                                title -> lines.add(Component.text(" • ", NamedTextColor.DARK_GRAY)
                                        .append(Component.text(id, NamedTextColor.WHITE))
                                        .append(Component.text(" — " + title,
                                                NamedTextColor.GRAY))),
                                () -> lines.add(Component.text(" • " + id, NamedTextColor.GRAY))));
            }
            CommandFeedback.deliver(scheduler, player, lines);
        });
    }

    private void usage(Player player, String label) {
        player.sendMessage(Component.text(
                "Usage: /" + label + " [shop | buy <id> | color <id|none> | title <id|none> "
                        + "| death <id|none> | unlocks | hud on|off]",
                NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!glyphs.settings().enabled() || !(sender instanceof Player)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return GlyphCatalog.all().stream()
                    .map(GlyphProduct::id)
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("color")) {
            return productSuggestions(args[1], GlyphProductType.NAME_COLOR);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) {
            return titleSuggestions(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("death")) {
            return productSuggestions(args[1], GlyphProductType.DEATH_MESSAGE);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("hud")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("on", "off").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private List<String> productSuggestions(String partial, GlyphProductType type) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>(List.of("none"));
        GlyphCatalog.all().stream()
                .filter(p -> p.type() == type)
                .map(GlyphProduct::id)
                .filter(id -> id.startsWith(prefix))
                .forEach(suggestions::add);
        return suggestions.stream().filter(s -> s.startsWith(prefix)).toList();
    }

    private List<String> titleSuggestions(String partial) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>(List.of("none"));
        GlyphCatalog.all().stream()
                .filter(p -> p.type() == GlyphProductType.TITLE)
                .map(GlyphProduct::id)
                .filter(id -> id.startsWith(prefix))
                .forEach(suggestions::add);
        return suggestions.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
