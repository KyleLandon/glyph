package com.glyph.core.bounty.command;

import com.glyph.api.economy.Money;
import com.glyph.api.player.PlayerApi;
import com.glyph.core.bounty.BountyRepository.PlaceResult;
import com.glyph.core.bounty.BountyRepository.TargetTotal;
import com.glyph.core.bounty.BountyService;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.economy.command.PlayerNameResolver;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * {@code /bounty} (GDD sections 25, 66):
 *
 * <ul>
 *   <li>{@code /bounty} — top wanted players</li>
 *   <li>{@code /bounty <player>} — active bounty on a player</li>
 *   <li>{@code /bounty add <player> <amount>} — place a bounty (escrowed)</li>
 * </ul>
 */
public final class BountyCommand implements CommandExecutor, TabCompleter {

    private final BountyService bounties;
    private final PlayerApi players;
    private final SchedulerAdapter scheduler;
    private final EconomySettings economy;

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public BountyCommand(BountyService bounties, PlayerApi players,
                         SchedulerAdapter scheduler, EconomySettings economy) {
        this.bounties = bounties;
        this.players = players;
        this.scheduler = scheduler;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!bounties.settings().enabled()) {
            sender.sendMessage(Component.text("Bounties are disabled.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            showTop(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("add")) {
            placeBounty(sender, label, args);
            return true;
        }
        if (args.length == 1) {
            showTarget(sender, args[0]);
            return true;
        }
        sender.sendMessage(Component.text(
                "Usage: /" + label + " [<player> | add <player> <amount>]", NamedTextColor.RED));
        return true;
    }

    private void showTop(CommandSender sender) {
        bounties.topTargets(10).whenComplete((top, error) -> {
            if (error != null) {
                deliver(sender, List.of(Component.text(
                        "Bounties are unavailable right now.", NamedTextColor.RED)));
                return;
            }
            List<Component> lines = new ArrayList<>();
            if (top.isEmpty()) {
                lines.add(Component.text("No active bounties. Place one with "
                        + "/bounty add <player> <amount>.", NamedTextColor.GRAY));
            } else {
                lines.add(Component.text("Most wanted:", NamedTextColor.GOLD));
                int rank = 1;
                for (TargetTotal target : top) {
                    lines.add(Component.text(" " + rank++ + ". ", NamedTextColor.GRAY)
                            .append(Component.text(target.targetName(), NamedTextColor.RED))
                            .append(Component.text(" — ", NamedTextColor.GRAY))
                            .append(Component.text(
                                    Money.of(target.total())
                                            .format(economy.currencySymbol()),
                                    NamedTextColor.GOLD))
                            .append(Component.text(" (" + target.count() + " bounty(ies))",
                                    NamedTextColor.DARK_GRAY)));
                }
            }
            deliver(sender, lines);
        });
    }

    private void showTarget(CommandSender sender, String name) {
        PlayerNameResolver.resolve(players, name)
                .thenCompose(target -> {
                    if (target.isEmpty()) {
                        return CompletableFuture.completedFuture((Component) Component.text(
                                "Unknown player: " + name, NamedTextColor.RED));
                    }
                    return bounties.activeTotal(target.get().uuid()).thenApply(total ->
                            total <= 0
                                    ? Component.text("No active bounty on "
                                            + target.get().name() + ".", NamedTextColor.GRAY)
                                    : Component.text("Bounty on " + target.get().name() + ": ",
                                                    NamedTextColor.GRAY)
                                            .append(Component.text(
                                                    Money.of(total)
                                                            .format(economy.currencySymbol()),
                                                    NamedTextColor.GOLD)));
                })
                .whenComplete((message, error) -> deliver(sender, List.of(error != null
                        ? Component.text("Bounties are unavailable right now.", NamedTextColor.RED)
                        : message)));
    }

    private void placeBounty(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player creator)) {
            sender.sendMessage(Component.text("Only players can place bounties.",
                    NamedTextColor.RED));
            return;
        }
        if (args.length != 3) {
            creator.sendMessage(Component.text("Usage: /" + label + " add <player> <amount>",
                    NamedTextColor.RED));
            return;
        }
        Money amount;
        try {
            amount = Money.parse(args[2]);
        } catch (IllegalArgumentException | ArithmeticException e) {
            creator.sendMessage(Component.text("Not a valid amount: " + args[2],
                    NamedTextColor.RED));
            return;
        }
        String symbol = economy.currencySymbol();
        if (amount.dollars() < bounties.settings().minimum()) {
            creator.sendMessage(Component.text("Minimum bounty is "
                    + Money.of(bounties.settings().minimum()).format(symbol) + ".",
                    NamedTextColor.RED));
            return;
        }
        if (!inFlight.add(creator.getUniqueId())) {
            creator.sendMessage(Component.text("Your previous bounty is still processing.",
                    NamedTextColor.RED));
            return;
        }

        PlayerNameResolver.resolve(players, args[1])
                .thenCompose(target -> {
                    if (target.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new Outcome(null, null));
                    }
                    if (target.get().uuid().equals(creator.getUniqueId())) {
                        return CompletableFuture.completedFuture(
                                new Outcome(target.get(), null));
                    }
                    return bounties.place(target.get().uuid(), creator.getUniqueId(),
                                    amount.dollars())
                            .thenApply(result -> new Outcome(target.get(), result));
                })
                .whenComplete((outcome, error) -> {
                    inFlight.remove(creator.getUniqueId());
                    if (error != null) {
                        deliver(creator, List.of(Component.text(
                                "Bounty failed — try again later.", NamedTextColor.RED)));
                        return;
                    }
                    respond(creator, args[1], amount, outcome);
                });
    }

    private record Outcome(PlayerNameResolver.PlayerRef target, PlaceResult result) { }

    private void respond(Player creator, String targetArg, Money amount, Outcome outcome) {
        String symbol = economy.currencySymbol();
        if (outcome.target() == null) {
            deliver(creator, List.of(Component.text("Unknown player: " + targetArg,
                    NamedTextColor.RED)));
            return;
        }
        if (outcome.result() == null) {
            deliver(creator, List.of(Component.text("You cannot place a bounty on yourself.",
                    NamedTextColor.RED)));
            return;
        }
        switch (outcome.result().status()) {
            case SUCCESS -> {
                deliver(creator, List.of(Component.text("Bounty of " + amount.format(symbol)
                                + " placed on " + outcome.target().name() + ".",
                        NamedTextColor.GREEN)));
                // Anarchy theatre: everyone hears about new blood money.
                String targetName = outcome.target().name();
                String formatted = amount.format(symbol);
                scheduler.runGlobal(() -> Bukkit.getServer().broadcast(Component.text()
                        .append(Component.text("A ", NamedTextColor.GRAY))
                        .append(Component.text(formatted, NamedTextColor.GOLD))
                        .append(Component.text(" bounty was placed on ", NamedTextColor.GRAY))
                        .append(Component.text(targetName, NamedTextColor.RED))
                        .append(Component.text(".", NamedTextColor.GRAY))
                        .build()));
            }
            case INSUFFICIENT_FUNDS -> deliver(creator, List.of(Component.text(
                    "You cannot afford " + amount.format(symbol) + ".", NamedTextColor.RED)));
            case ACCOUNT_NOT_FOUND -> deliver(creator, List.of(Component.text(
                    "Unknown player: " + targetArg, NamedTextColor.RED)));
            case FAILED -> deliver(creator, List.of(Component.text(
                    "Bounty failed — try again later.", NamedTextColor.RED)));
        }
    }

    private void deliver(CommandSender sender, List<Component> messages) {
        if (sender instanceof Player player) {
            scheduler.runForEntity(player, () -> messages.forEach(player::sendMessage), null);
        } else {
            messages.forEach(sender::sendMessage);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return List.of("add").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
