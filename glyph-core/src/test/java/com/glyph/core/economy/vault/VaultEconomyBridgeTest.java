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
        public Optional<Long> balance(UUID playerUuid) {
            return Optional.ofNullable(balances.get(playerUuid));
        }

        @Override
        public MutationOutcome transfer(UUID source, UUID destination,
                                        long amount, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MutationOutcome adminAdjust(UUID playerUuid, AdminOperation operation,
                                           long amount, UUID actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MutationOutcome externalAdjust(UUID playerUuid, AdminOperation operation,
                                              long amount, TransactionType type, String reason) {
            lastType = type;
            lastReason = reason;
            Long balance = balances.get(playerUuid);
            if (balance == null) {
                return MutationOutcome.failure(TransferResult.Status.ACCOUNT_NOT_FOUND);
            }
            long target = operation == AdminOperation.ADD
                    ? balance + amount : balance - amount;
            if (target < 0) {
                return MutationOutcome.failure(TransferResult.Status.INSUFFICIENT_FUNDS);
            }
            balances.put(playerUuid, target);
            return new MutationOutcome(
                    TransferResult.success(UUID.randomUUID(), Money.of(target)), target, -1);
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

        @Override
        public List<PlaytimeLeader> topPlaytime(int limit) {
            return List.of();
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
        assertThat(bridge.fractionalDigits()).isZero();
        assertThat(bridge.format(1234.0)).isEqualTo("$1,234");
    }

    @Test
    void conversionRejectsHostileDoubles() {
        assertThat(VaultEconomyBridge.toDollars(9.99)).contains(10L); // rounds half up
        assertThat(VaultEconomyBridge.toDollars(0.5)).contains(1L);
        assertThat(VaultEconomyBridge.toDollars(0.4)).contains(0L);
        assertThat(VaultEconomyBridge.toDollars(-0.01)).isEmpty();
        assertThat(VaultEconomyBridge.toDollars(Double.NaN)).isEmpty();
        assertThat(VaultEconomyBridge.toDollars(Double.POSITIVE_INFINITY)).isEmpty();
        assertThat(VaultEconomyBridge.toDollars(1e19)).isEmpty(); // overflows a long
    }

    @Test
    void balanceReadsAndAccountChecks() {
        economy.balances.put(uuid, 1234L);

        assertThat(bridge.hasAccount(player)).isTrue();
        assertThat(bridge.getBalance(player)).isEqualTo(1234.0);
        assertThat(bridge.has(player, 1234.0)).isTrue();
        assertThat(bridge.has(player, 1235.0)).isFalse();
        assertThat(bridge.hasAccount(offline(UUID.randomUUID()))).isFalse();
    }

    @Test
    void depositMintsAsSystemReward() {
        economy.balances.put(uuid, 0L);

        EconomyResponse response = bridge.depositPlayer(player, 5.0);

        assertThat(response.type).isEqualTo(ResponseType.SUCCESS);
        assertThat(response.balance).isEqualTo(5.0);
        assertThat(economy.balances).containsEntry(uuid, 5L);
        assertThat(economy.lastType).isEqualTo(TransactionType.SYSTEM_REWARD);
        assertThat(economy.lastReason).isEqualTo("vault bridge");
    }

    @Test
    void withdrawBurnsAsSystemSink() {
        economy.balances.put(uuid, 10L);

        EconomyResponse response = bridge.withdrawPlayer(player, 4.0);

        assertThat(response.type).isEqualTo(ResponseType.SUCCESS);
        assertThat(response.balance).isEqualTo(6.0);
        assertThat(economy.lastType).isEqualTo(TransactionType.SYSTEM_SINK);
    }

    @Test
    void withdrawBeyondBalanceFails() {
        economy.balances.put(uuid, 1L);

        EconomyResponse response = bridge.withdrawPlayer(player, 5.0);

        assertThat(response.type).isEqualTo(ResponseType.FAILURE);
        assertThat(response.errorMessage).isEqualTo("Insufficient funds");
        assertThat(economy.balances).containsEntry(uuid, 1L);
    }

    @Test
    void negativeAmountFails() {
        economy.balances.put(uuid, 100L);

        assertThat(bridge.depositPlayer(player, -5).type).isEqualTo(ResponseType.FAILURE);
        assertThat(bridge.withdrawPlayer(player, Double.NaN).type).isEqualTo(ResponseType.FAILURE);
        assertThat(economy.balances).containsEntry(uuid, 100L);
    }

    @Test
    void unknownAccountFails() {
        EconomyResponse response = bridge.depositPlayer(player, 5.00);

        assertThat(response.type).isEqualTo(ResponseType.FAILURE);
        assertThat(response.errorMessage).isEqualTo("No account");
    }

    @Test
    void databaseDownFailsFastEverywhere() {
        economy.balances.put(uuid, 1000L);
        databaseReady.set(false);

        assertThat(bridge.getBalance(player)).isZero();
        assertThat(bridge.hasAccount(player)).isFalse();
        assertThat(bridge.depositPlayer(player, 1.0).type).isEqualTo(ResponseType.FAILURE);
        assertThat(bridge.withdrawPlayer(player, 1.0).type).isEqualTo(ResponseType.FAILURE);
        assertThat(economy.balances).containsEntry(uuid, 1000L);
    }

    @Test
    void successNotifiesBalanceListeners() {
        economy.balances.put(uuid, 0L);
        List<Long> observed = new ArrayList<>();
        service.addBalanceListener((id, balance) -> observed.add(balance.dollars()));

        bridge.depositPlayer(player, 3.0);

        assertThat(observed).containsExactly(3L);
    }

    @Test
    void nameBasedLookupsUseThePlayersTable() {
        economy.balances.put(uuid, 750L);
        players.byName.put("steve", uuid);

        assertThat(bridge.getBalance("Steve")).isEqualTo(750.0);
        assertThat(bridge.hasAccount("Steve")).isTrue();
        assertThat(bridge.withdrawPlayer("Steve", 50.0).type).isEqualTo(ResponseType.SUCCESS);
        assertThat(bridge.getBalance("Nobody")).isZero();
        assertThat(bridge.depositPlayer("Nobody", 1.0).type).isEqualTo(ResponseType.FAILURE);
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
