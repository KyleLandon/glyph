package com.glyph.core.smp.command;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TransactionType;
import com.glyph.api.economy.TransferResult;
import com.glyph.core.claims.GriefPreventionAccess;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.config.SmpSettings;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.economy.command.CommandFeedback;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

/**
 * {@code /claimblocks} and {@code /claimblocks buy [packs]}.
 * One pack is {@code pack-size} blocks for {@code pack-price} dollars.
 */
public final class ClaimBlocksCommand implements CommandExecutor, TabCompleter {

    private final EconomyService economy;
    private final SmpSettings smp;
    private final EconomySettings money;
    private final SchedulerAdapter scheduler;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public ClaimBlocksCommand(
            EconomyService economy,
            SmpSettings smp,
            EconomySettings money,
            SchedulerAdapter scheduler) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.smp = Objects.requireNonNull(smp, "smp");
        this.money = Objects.requireNonNull(money, "money");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can buy claim blocks.",
                    NamedTextColor.RED));
            return true;
        }
        if (!GriefPreventionAccess.present()) {
            player.sendMessage(Component.text("Land claims are not loaded.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            show(player);
            return true;
        }
        if (!args[0].equalsIgnoreCase("buy")) {
            player.sendMessage(Component.text(
                    "Usage: /claimblocks   or   /claimblocks buy [packs]",
                    NamedTextColor.RED));
            return true;
        }
        int packs = 1;
        if (args.length >= 2) {
            try {
                packs = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Packs must be a whole number.",
                        NamedTextColor.RED));
                return true;
            }
        }
        if (packs < 1 || packs > 100) {
            player.sendMessage(Component.text("Buy 1 to 100 packs at a time.", NamedTextColor.RED));
            return true;
        }
        buy(player, packs);
        return true;
    }

    private void show(Player player) {
        int remaining = GriefPreventionAccess.remainingClaimBlocks(player.getUniqueId()).orElse(0);
        Money pack = Money.of(smp.claimBlockPackPrice());
        player.sendMessage(Component.text(
                "Claim blocks remaining: " + remaining, NamedTextColor.GOLD));
        player.sendMessage(Component.text(
                "Buy " + smp.claimBlockPackSize() + " for "
                        + pack.format(money.currencySymbol())
                        + " — /claimblocks buy [packs]",
                NamedTextColor.GRAY));
    }

    private void buy(Player player, int packs) {
        if (!inFlight.add(player.getUniqueId())) {
            player.sendMessage(Component.text("Purchase already processing.", NamedTextColor.YELLOW));
            return;
        }
        long price;
        int blocks;
        try {
            price = Math.multiplyExact(smp.claimBlockPackPrice(), packs);
            blocks = Math.multiplyExact(smp.claimBlockPackSize(), packs);
        } catch (ArithmeticException e) {
            inFlight.remove(player.getUniqueId());
            player.sendMessage(Component.text("That many packs is too large.", NamedTextColor.RED));
            return;
        }
        Money cost = Money.of(price);
        economy.systemAdjust(
                player.getUniqueId(), AdminOperation.REMOVE, cost,
                TransactionType.SYSTEM_SINK, "claim block pack x" + packs)
                .whenComplete((result, error) -> {
                    try {
                        if (error != null || result == null || !result.isSuccess()) {
                            TransferResult.Status status = result == null
                                    ? TransferResult.Status.FAILED : result.status();
                            CommandFeedback.deliver(scheduler, player, messageFor(status, cost));
                            return;
                        }
                        scheduler.runForEntity(player, () -> {
                            var remaining = GriefPreventionAccess.addBonusClaimBlocks(
                                    player.getUniqueId(), blocks);
                            if (remaining.isEmpty()) {
                                economy.systemAdjust(
                                        player.getUniqueId(), AdminOperation.ADD, cost,
                                        TransactionType.SYSTEM_REWARD,
                                        "claim block pack refund");
                                player.sendMessage(Component.text(
                                        "Could not add claim blocks. Refunded.",
                                        NamedTextColor.RED));
                                return;
                            }
                            player.sendMessage(Component.text(
                                    "Bought " + blocks + " claim blocks for "
                                            + cost.format(money.currencySymbol())
                                            + ". Remaining: " + remaining.get(),
                                    NamedTextColor.GREEN));
                        }, null);
                    } finally {
                        inFlight.remove(player.getUniqueId());
                    }
                });
    }

    private Component messageFor(TransferResult.Status status, Money cost) {
        return switch (status) {
            case INSUFFICIENT_FUNDS -> Component.text(
                    "Need " + cost.format(money.currencySymbol()) + ".", NamedTextColor.RED);
            case ACCOUNT_NOT_FOUND -> Component.text("No economy account.", NamedTextColor.RED);
            default -> Component.text("Purchase failed. Try again.", NamedTextColor.RED);
        };
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && "buy".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("buy");
        }
        return List.of();
    }
}
