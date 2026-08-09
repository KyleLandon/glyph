package com.glyph.core.economy.vault;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TransactionType;
import com.glyph.core.economy.EconomyRepository;
import com.glyph.core.economy.EconomyRepository.MutationOutcome;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.player.PlayerRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;
import org.slf4j.Logger;

/**
 * Classic Vault {@code Economy} provider backed by the Glyph economy, so
 * third-party plugins (shops, jobs, etc.) can read and mutate balances.
 *
 * <p><b>Threading:</b> Vault's API is synchronous, so calls execute the same
 * locked single-row SQL transactions the rest of the economy uses, directly
 * on the calling thread (see ADR-009). Locally that is single-digit
 * milliseconds; when the database is down every call fails fast without
 * touching the pool. Do not use Vault for hot paths — trusted plugins should
 * use the async {@code GlyphApi.economy()} instead.</p>
 *
 * <p><b>Semantics:</b> deposits mint as {@code SYSTEM_REWARD}, withdrawals
 * burn as {@code SYSTEM_SINK}; every mutation is ledgered with the reason
 * {@code "vault bridge"}. Worlds are ignored (one economy per network) and
 * banks are unsupported.</p>
 */
public final class VaultEconomyBridge implements Economy {

    private static final String REASON = "vault bridge";

    private final EconomyRepository economyRepository;
    private final PlayerRepository playerRepository;
    private final EconomyService economyService;
    private final BooleanSupplier databaseReady;
    private final String currencySymbol;
    private final Logger logger;

    public VaultEconomyBridge(
            EconomyRepository economyRepository,
            PlayerRepository playerRepository,
            EconomyService economyService,
            BooleanSupplier databaseReady,
            String currencySymbol,
            Logger logger) {
        this.economyRepository = economyRepository;
        this.playerRepository = playerRepository;
        this.economyService = economyService;
        this.databaseReady = databaseReady;
        this.currencySymbol = currencySymbol;
        this.logger = logger;
    }

    // --- provider identity -------------------------------------------------

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "GlyphEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return toDollars(amount)
                .map(dollars -> Money.of(dollars).format(currencySymbol))
                .orElseGet(() -> currencySymbol + String.format("%.0f", amount));
    }

    @Override
    public String currencyNamePlural() {
        return "";
    }

    @Override
    public String currencyNameSingular() {
        return "";
    }

    // --- accounts ----------------------------------------------------------

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return guarded(() -> economyRepository.balance(player.getUniqueId()).isPresent(),
                false);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName) {
        return resolve(playerName).map(uuid -> guarded(
                () -> economyRepository.balance(uuid).isPresent(), false)).orElse(false);
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return guarded(() -> economyRepository.ensureAccount(player.getUniqueId()), false);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName) {
        return resolve(playerName)
                .map(uuid -> guarded(() -> economyRepository.ensureAccount(uuid), false))
                .orElse(false);
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    // --- balance reads -----------------------------------------------------

    @Override
    public double getBalance(OfflinePlayer player) {
        return balance(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return balance(player.getUniqueId());
    }

    @Override
    @Deprecated
    public double getBalance(String playerName) {
        return resolve(playerName).map(this::balance).orElse(0.0);
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    // --- mutations ---------------------------------------------------------

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return mutate(player.getUniqueId(), amount, AdminOperation.REMOVE,
                TransactionType.SYSTEM_SINK);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return resolve(playerName)
                .map(uuid -> mutate(uuid, amount, AdminOperation.REMOVE, TransactionType.SYSTEM_SINK))
                .orElseGet(() -> unknownPlayer(playerName));
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return mutate(player.getUniqueId(), amount, AdminOperation.ADD,
                TransactionType.SYSTEM_REWARD);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return resolve(playerName)
                .map(uuid -> mutate(uuid, amount, AdminOperation.ADD, TransactionType.SYSTEM_REWARD))
                .orElseGet(() -> unknownPlayer(playerName));
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    // --- banks: unsupported (GDD has no bank concept) ----------------------

    @Override
    public EconomyResponse createBank(String name, String player) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return banksUnsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return banksUnsupported();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    // --- internals ----------------------------------------------------------

    private double balance(UUID uuid) {
        return guarded(() -> economyRepository.balance(uuid).orElse(0L), 0L);
    }

    private EconomyResponse mutate(UUID uuid, double amount,
                                   AdminOperation operation, TransactionType type) {
        Optional<Long> dollars = toDollars(amount);
        if (dollars.isEmpty()) {
            return new EconomyResponse(amount, 0,
                    ResponseType.FAILURE, "Invalid amount");
        }
        if (!databaseReady.getAsBoolean()) {
            return new EconomyResponse(amount, 0,
                    ResponseType.FAILURE, "Economy database unavailable");
        }
        MutationOutcome outcome;
        try {
            outcome = economyRepository.externalAdjust(
                    uuid, operation, dollars.get(), type, REASON);
        } catch (Exception e) {
            logger.error("Vault bridge {} failed for {}", operation, uuid, e);
            return new EconomyResponse(amount, 0,
                    ResponseType.FAILURE, "Economy operation failed");
        }
        double balanceAfter = Math.max(0, outcome.sourceBalanceAfter());
        return switch (outcome.result().status()) {
            case SUCCESS -> {
                economyService.publishBalanceChange(uuid,
                        Money.of(outcome.sourceBalanceAfter()));
                yield new EconomyResponse(amount, balanceAfter, ResponseType.SUCCESS, null);
            }
            case INSUFFICIENT_FUNDS -> new EconomyResponse(amount, balanceAfter,
                    ResponseType.FAILURE, "Insufficient funds");
            case ACCOUNT_NOT_FOUND -> new EconomyResponse(amount, 0,
                    ResponseType.FAILURE, "No account");
            default -> new EconomyResponse(amount, balanceAfter,
                    ResponseType.FAILURE, "Economy operation failed");
        };
    }

    /**
     * Converts a Vault double to whole dollars (the economy has no cents,
     * fractions round half-up), rejecting NaN, infinity, negatives and
     * values that overflow.
     */
    static Optional<Long> toDollars(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(BigDecimal.valueOf(amount)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact());
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
    }

    /** Name lookups go straight to the players table (UUID is authoritative). */
    private Optional<UUID> resolve(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }
        return guarded(() -> playerRepository.findByUsername(playerName)
                .map(profile -> profile.uuid()), Optional.<UUID>empty());
    }

    /** Fail-fast wrapper: no pool access when the database is down. */
    private <T> T guarded(java.util.function.Supplier<T> query, T fallback) {
        if (!databaseReady.getAsBoolean()) {
            return fallback;
        }
        try {
            return query.get();
        } catch (Exception e) {
            logger.error("Vault bridge query failed", e);
            return fallback;
        }
    }

    private static EconomyResponse unknownPlayer(String name) {
        return new EconomyResponse(0, 0, ResponseType.FAILURE, "Unknown player: " + name);
    }

    private static EconomyResponse banksUnsupported() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED,
                "Glyph does not support banks");
    }
}
