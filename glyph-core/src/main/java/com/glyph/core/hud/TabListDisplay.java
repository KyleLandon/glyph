package com.glyph.core.hud;

import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.config.TabSettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.stats.StatsService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.slf4j.Logger;

/**
 * Tab list rows: {@code Name  $100  3☠} plus a branded header/footer.
 *
 * <p>Money updates ride {@link com.glyph.core.economy.EconomyService} balance
 * listeners. Deaths load a join snapshot (DB + unflushed buffer), then only
 * count deaths that happen after that baseline is applied.</p>
 */
public final class TabListDisplay implements Listener {

    private final SchedulerAdapter scheduler;
    private final TabSettings tab;
    private final EconomySettings economy;
    private final StatsService stats;
    private final Logger logger;

    private final Map<UUID, Row> rows = new ConcurrentHashMap<>();

    public TabListDisplay(
            SchedulerAdapter scheduler,
            TabSettings tab,
            EconomySettings economy,
            StatsService stats,
            Logger logger) {
        this.scheduler = scheduler;
        this.tab = tab;
        this.economy = economy;
        this.stats = stats;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!tab.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        rows.put(player.getUniqueId(), new Row());
        scheduler.runForEntity(player, () -> {
            applyHeaderFooter(player);
            applyName(player);
        }, null);
    }

    /**
     * Called after join persistence / starting-balance mint. Loads death
     * baseline; money arrives via {@link #updateBalance}.
     */
    public void onJoinPersisted(UUID uuid) {
        if (!tab.enabled()) {
            return;
        }
        Row row = rows.computeIfAbsent(uuid, id -> new Row());
        stats.deathsSnapshot(uuid).whenComplete((deaths, error) -> {
            if (error != null) {
                logger.debug("Tab list: death snapshot failed for {}", uuid, error);
                row.applyBaseline(0L);
            } else {
                row.applyBaseline(deaths == null ? 0L : deaths);
            }
            apply(uuid);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        rows.remove(event.getPlayer().getUniqueId());
        Player player = event.getPlayer();
        scheduler.runForEntity(player, () -> player.playerListName(null), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!tab.enabled()) {
            return;
        }
        UUID uuid = event.getEntity().getUniqueId();
        Row row = rows.computeIfAbsent(uuid, id -> new Row());
        if (row.noteDeath()) {
            apply(uuid);
        }
    }

    /** Economy balance listener — any thread. */
    public void updateBalance(UUID uuid, Money balance) {
        if (!tab.enabled()) {
            return;
        }
        Row row = rows.computeIfAbsent(uuid, id -> new Row());
        row.money = balance;
        apply(uuid);
    }

    private void apply(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        scheduler.runForEntity(player, () -> applyName(player), null);
    }

    private void applyHeaderFooter(Player player) {
        Component header = Component.text(tab.header(), NamedTextColor.WHITE)
                .decorate(TextDecoration.BOLD);
        Component footer = Component.text(tab.footer(), NamedTextColor.DARK_GRAY);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private void applyName(Player player) {
        if (!player.isOnline()) {
            return;
        }
        Row row = rows.get(player.getUniqueId());
        Component name = formatRow(
                player.getName(),
                row == null ? null : row.money,
                row == null ? 0L : row.deaths(),
                economy.currencySymbol());
        player.playerListName(name);
    }

    /**
     * {@code Name  $100  3☠} — compact so wide tabs stay readable.
     */
    static Component formatRow(String username, Money money, long deaths, String symbol) {
        String cash = money == null
                ? symbol + " —"
                : MoneyHud.formatHud(money, symbol);
        return Component.text(username, NamedTextColor.WHITE)
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(cash, NamedTextColor.GREEN))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(deaths + "☠", NamedTextColor.RED));
    }

    /**
     * Baseline replaces the total (snapshot already has buffered deaths).
     * Only deaths after {@link #applyBaseline} increment further — deaths
     * during the load window are ignored here because they are in the peek.
     */
    private static final class Row {
        volatile Money money;
        private final AtomicLong totalDeaths = new AtomicLong(0);
        private final AtomicBoolean baselineReady = new AtomicBoolean(false);

        /** @return true when the visible total changed */
        boolean noteDeath() {
            if (!baselineReady.get()) {
                // Still loading — snapshot peek will include this death.
                return false;
            }
            totalDeaths.incrementAndGet();
            return true;
        }

        void applyBaseline(long snapshot) {
            totalDeaths.set(Math.max(0L, snapshot));
            baselineReady.set(true);
        }

        long deaths() {
            return totalDeaths.get();
        }
    }
}
