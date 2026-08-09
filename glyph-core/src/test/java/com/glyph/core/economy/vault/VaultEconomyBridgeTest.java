package com.glyph.core.economy.vault;

import static org.assertj.core.api.Assertions.assertThat;

import com.glyph.api.economy.EconomyApi.AdminOperation;
import com.glyph.api.economy.LedgerEntry;
import com.glyph.api.economy.Money;
import com.glyph.api.economy.TopBalance;
import com.glyph.api.economy.TransactionType;
import com.glyph.api.economy.TransferResult;
import com.glyph.api.player.PlayerProfile;
import com.glyph.core.economy.EconomyRepository;
import com.glyph.core.economy.EconomyService;
import com.glyph.core.player.PlayerRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class VaultEconomyBridgeTest {

    private static final class InMemoryEconomy implements EconomyRepository {
        final Map<UUID, Long> balances = new HashMap<>();
        TransactionType lastType;
        String lastReason;

        @Override
        public Optional<Long> balanceMinor(UUID playerUuid) {
            return Optional.ofNullable(balances.get(playerUuid));
        }

        @Override
        public MutationOutcome transfer(UUID source, UUID destination,
                                        long amountMinor, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MutationOutcome adminAdjust(UUID playerUuid, AdminOperation operation,
                                           long amountMinor, UUID actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MutationOutcome externalAdjust(UUID playerUuid, AdminOperation operation,
                                              long amountMinor, TransactionType type, String reason) {
            lastType = type;
            lastReason = reason;
            Long balance = balances.get(playerUuid);
            if (balance == null) {
                return MutationOutcome.failure(TransferResult.Status.ACCOUNT_NOT_FOUND);
            }
            long target = operation == AdminOperation.ADD
                    ? balance + amountMinor : balance - amountMinor;
            if (target < 0) {
                return MutationOutcome.failure(TransferResult.Status.INSUFFICIENT_FUNDS);
            }
            balances.put(playerUuid, target);
            return new MutationOutcome(
                    TransferResult.success(UUID.randomUUID(), Money.ofMinor(target)), target, -1);
        }

        @Override
        public boolean ensureAccount(UUID playerUuid) {
            balances.putIfAbsent(playerUuid, 0L);
            return true;
        }

        @Override
        public List<TopBalance> topBalances(int limit) {
            return List.of();
        }

        @Override
        public List<LedgerEntry> history(UUID playerUuid, int limit) {
            return List.of();
        }
    }

    private static final class InMemoryPlayers implements PlayerRepository {
        final Map<String, UUID> byName = new HashMap<>();

        @Override
        public JoinResult recordJoin(UUID uuid, String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordQuit(UUID uuid, long sessionSeconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PlayerProfile> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerProfile> findByUsername(String username) {
            return Optional.ofNullable(byName.get(username.toLowerCase()))
                    .map(uuid -> new PlayerProfile(uuid, username,
                            Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, 0));
        }
    }

    /** OfflinePlayer stub without pulling in a running server or mockito. */
    private static OfflinePlayer offline(UUID uuid) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                VaultEconomyBridgeTest.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> "Stub";
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "OfflinePlayer(" + uuid + ")";
                    default -> null;
                });
    }

    private final InMemoryEconomy economy = new InMemoryEconomy();
    private final InMemoryPlayers players = new InMemoryPlayers();
    private final AtomicBoolean databaseReady = new AtomicBoolean(true);
    private final EconomyService service = new EconomyService(
            economy, databaseReady::get, Runnable::run, LoggerFactory.getLogger("test"));
    private final VaultEconomyBridge bridge = new VaultEconomyBridge(
            economy, players, service, databaseReady::get, "$", LoggerFactory.getLogger("test"));

    private final UUID uuid = UUID.randomUUID();
    private final OfflinePlayer player = offline(uuid);

    @Test
    void identityAndFormatting() {
        assertThat(bridge.isEnabled()).isTrue();
        assertThat(bridge.getName()).isEqualTo("GlyphEconomy");
        assertThat(bridge.hasBankSupport()).isFalse();
        assertThat(bridge.fractionalDigits()).isEqualTo(2);
        assertThat(bridge.format(1234.56)).isEqualTo("$1,234.56");
    }

    @Test
    void conversionRejectsHostileDoubles() {
        assertThat(VaultEconomyBridge.toMinor(9.99)).contains(999L);
        assertThat(VaultEconomyBridge.toMinor(0.005)).contains(1L); // rounds half up
        assertThat(VaultEconomyBridge.toMinor(-0.01)).isEmpty();
        assertThat(VaultEconomyBridge.toMinor(Double.NaN)).isEmpty();
        assertThat(VaultEconomyBridge.toMinor(Double.POSITIVE_INFINITY)).isEmpty();
        assertThat(VaultEconomyBridge.toMinor(1e18)).isEmpty(); // overflows cents
    }

    @Test
    void balanceReadsAndAccountChecks() {
        economy.balances.put(uuid, 12_34L);

        assertThat(bridge.hasAccount(player)).isTrue();
        assertThat(bridge.getBalance(player)).isEqualTo(12.34);
        assertThat(bridge.has(player, 12.34)).isTrue();
        assertThat(bridge.has(player, 12.35)).isFalse();
        assertThat(bridge.hasAccount(offline(UUID.randomUUID()))).isFalse();
    }

    @Test
    void depositMintsAsSystemReward() {
        economy.balances.put(uuid, 0L);

        EconomyResponse response = bridge.depositPlayer(player, 5.25);

        assertThat(response.type).isEqualTo(ResponseType.SUCCESS);
        assertThat(response.balance).isEqualTo(5.25);
        assertThat(economy.balances).containsEntry(uuid, 5_25L);
        assertThat(economy.lastType).isEqualTo(TransactionType.SYSTEM_REWARD);
        assertThat(economy.lastReason).isEqualTo("vault bridge");
    }

    @Test
    void withdrawBurnsAsSystemSink() {
        economy.balances.put(uuid, 10_00L);

        EconomyResponse response = bridge.withdrawPlayer(player, 4.00);

        assertThat(response.type).isEqualTo(ResponseType.SUCCESS);
        assertThat(response.balance).isEqualTo(6.00);
        assertThat(economy.lastType).isEqualTo(TransactionType.SYSTEM_SINK);
    }

    @Test
    void withdrawBeyondBalanceFails() {
        economy.balances.put(uuid, 1_00L);

        EconomyResponse response = bridge.withdrawPlayer(player, 5.00);

        assertThat(response.type).isEqualTo(ResponseType.FAILURE);
        assertThat(response.errorMessage).isEqualTo("Insufficient funds");
        assertThat(economy.balances).containsEntry(uuid, 1_00L);
    }

    @Test
    void negativeAmountFails() {
        economy.balances.put(uuid, 1_00L);

        assertThat(bridge.depositPlayer(player, -5).type).isEqualTo(ResponseType.FAILURE);
        assertThat(bridge.withdrawPlayer(player, Double.NaN).type).isEqualTo(ResponseType.FAILURE);
        assertThat(economy.balances).containsEntry(uuid, 1_00L);
    }

    @Test
    void unknownAccountFails() {
        EconomyResponse response = bridge.depositPlayer(player, 5.00);

        assertThat(response.type).isEqualTo(ResponseType.FAILURE);
        assertThat(response.errorMessage).isEqualTo("No account");
    }

    @Test
    void databaseDownFailsFastEverywhere() {
        economy.balances.put(uuid, 10_00L);
        databaseReady.set(false);

        assertThat(bridge.getBalance(player)).isZero();
        assertThat(bridge.hasAccount(player)).isFalse();
        assertThat(bridge.depositPlayer(player, 1.00).type).isEqualTo(ResponseType.FAILURE);
        assertThat(bridge.withdrawPlayer(player, 1.00).type).isEqualTo(ResponseType.FAILURE);
        assertThat(economy.balances).containsEntry(uuid, 10_00L);
    }

    @Test
    void successNotifiesBalanceListeners() {
        economy.balances.put(uuid, 0L);
        List<Long> observed = new ArrayList<>();
        service.addBalanceListener((id, balance) -> observed.add(balance.minorUnits()));

        bridge.depositPlayer(player, 3.00);

        assertThat(observed).containsExactly(3_00L);
    }

    @Test
    void nameBasedLookupsUseThePlayersTable() {
        economy.balances.put(uuid, 7_50L);
        players.byName.put("steve", uuid);

        assertThat(bridge.getBalance("Steve")).isEqualTo(7.50);
        assertThat(bridge.hasAccount("Steve")).isTrue();
        assertThat(bridge.withdrawPlayer("Steve", 0.50).type).isEqualTo(ResponseType.SUCCESS);
        assertThat(bridge.getBalance("Nobody")).isZero();
        assertThat(bridge.depositPlayer("Nobody", 1.00).type).isEqualTo(ResponseType.FAILURE);
    }

    @Test
    void createPlayerAccountIsIdempotent() {
        assertThat(bridge.createPlayerAccount(player)).isTrue();
        assertThat(bridge.createPlayerAccount(player)).isTrue();
        assertThat(economy.balances).containsEntry(uuid, 0L);
    }

    @Test
    void banksAreNotImplemented() {
        assertThat(bridge.createBank("bank", player).type)
                .isEqualTo(ResponseType.NOT_IMPLEMENTED);
        assertThat(bridge.bankDeposit("bank", 1.0).type)
                .isEqualTo(ResponseType.NOT_IMPLEMENTED);
        assertThat(bridge.getBanks()).isEmpty();
    }
}
