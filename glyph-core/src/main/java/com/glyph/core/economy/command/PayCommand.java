package com.glyph.core.economy.command;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TransferResult;
import com.glyph.api.player.PlayerApi;
import com.glyph.core.command.CommandTabs;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * {@code /pay <player> <amount>} — atomic player-to-player transfer.
 *
 * <p>Exploit resistance (GDD section 85): amounts go through strict
 * {@link Money#parse}; an in-flight guard rejects double-clicked or
 * packet-duplicated commands while a payment is pending; the repository's
 * idempotency and row locking handle everything below.</p>
 */
public final class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyApi economy;
    private final PlayerApi players;
    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public PayCommand(EconomyApi economy, PlayerApi players,
                      SchedulerAdapter scheduler, EconomySettings settings) {
        this.economy = economy;
        this.players = players;
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage(Component.text("Only players can pay.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2) {
            payer.sendMessage(Component.text("Usage: /" + label + " <player> <amount>",
                    NamedTextColor.RED));
            return true;
        }

        Money amount;
        try {
            amount = Money.parse(args[1]);
        } catch (IllegalArgumentException | ArithmeticException e) {
            payer.sendMessage(Component.text("Not a valid amount: " + args[1], NamedTextColor.RED));
            return true;
        }
        if (!amount.isPositive()) {
            payer.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
            return true;
        }

        if (!inFlight.add(payer.getUniqueId())) {
            payer.sendMessage(Component.text("Your previous payment is still processing.",
                    NamedTextColor.RED));
            return true;
        }

        PlayerNameResolver.resolve(players, args[0])
                .thenCompose(target -> {
                    if (target.isEmpty()) {
                        return java.util.concurrent.CompletableFuture.completedFuture(
                                new Outcome(null, TransferResult.failure(
                                        TransferResult.Status.ACCOUNT_NOT_FOUND)));
                    }
                    String idempotencyKey = "pay:" + UUID.randomUUID();
                    return economy.transfer(payer.getUniqueId(), target.get().uuid(),
                                    amount, idempotencyKey)
                            .thenApply(result -> new Outcome(target.get(), result));
                })
                .whenComplete((outcome, error) -> {
                    inFlight.remove(payer.getUniqueId());
                    if (error != null) {
                        CommandFeedback.deliver(scheduler, payer, Component.text(
                                "Payment failed — try again later.", NamedTextColor.RED));
                        return;
                    }
                    respond(payer, args[0], amount, outcome);
                });
        return true;
    }

    private record Outcome(PlayerNameResolver.PlayerRef target, TransferResult result) { }

    private void respond(Player payer, String targetArg, Money amount, Outcome outcome) {
        String formatted = amount.format(settings.currencySymbol());
        Component message = switch (outcome.result().status()) {
            case SUCCESS -> Component.text("Paid " + formatted + " to "
                            + outcome.target().name() + ".", NamedTextColor.GREEN)
                    .append(outcome.result().newBalance()
                            .map(balance -> Component.text(" Balance: "
                                            + balance.format(settings.currencySymbol()),
                                    NamedTextColor.GRAY))
                            .orElse(Component.empty()));
            case INSUFFICIENT_FUNDS -> Component.text("You cannot afford " + formatted + ".",
                    NamedTextColor.RED);
            case ACCOUNT_NOT_FOUND -> Component.text("Unknown player: " + targetArg,
                    NamedTextColor.RED);
            case SELF_PAYMENT -> Component.text("You cannot pay yourself.", NamedTextColor.RED);
            case INVALID_AMOUNT -> Component.text("Invalid amount.", NamedTextColor.RED);
            case DUPLICATE_REQUEST -> Component.text("Duplicate payment ignored.",
                    NamedTextColor.RED);
            case FAILED -> Component.text("Payment failed — try again later.", NamedTextColor.RED);
        };
        CommandFeedback.deliver(scheduler, payer, message);

        if (outcome.result().isSuccess()) {
            Player recipient = Bukkit.getPlayer(outcome.target().uuid());
            if (recipient != null) {
                CommandFeedback.deliver(scheduler, recipient, Component.text(
                        "Received " + formatted + " from " + payer.getName() + ".",
                        NamedTextColor.GREEN));
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return CommandTabs.onlinePlayers(sender, args[0]);
        }
        return List.of();
    }
}
