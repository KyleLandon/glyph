package com.glyph.core.hud;

import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.config.GlyphCurrencySettings;
import com.glyph.core.config.TabSettings;
import com.glyph.core.glyphs.GlyphsService;
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
 * Tab list rows: {@code [Title] Name  $12.4k  ✦13  ☠29} plus a branded header/footer.
 */
public final class TabListDisplay implements Listener {

    private final SchedulerAdapter scheduler;
    private final TabSettings tab;
    private final EconomySettings economy;
    private final GlyphCurrencySettings glyphSettings;
    private final GlyphsService glyphs;
    private final StatsService stats;
    private final Logger logger;

    private final Map<UUID, Row> rows = new ConcurrentHashMap<>();

    public TabListDisplay(
            SchedulerAdapter scheduler,
            TabSettings tab,
            EconomySettings economy,
            GlyphCurrencySettings glyphSettings,
            GlyphsService glyphs,
            StatsService stats,
            Logger logger) {
        this.scheduler = scheduler;
        this.tab = tab;
        this.economy = economy;
        this.glyphSettings = glyphSettings;
        this.glyphs = glyphs;
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
     * baseline; money and glyphs arrive via listeners.
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

    /** Glyphs balance listener — any thread. */
    public void updateGlyphs(UUID uuid, Long balance) {
        if (!tab.enabled() || !glyphSettings.enabled()) {
            return;
        }
        Row row = rows.computeIfAbsent(uuid, id -> new Row());
        row.glyphs = balance;
        apply(uuid);
    }

    /** Glyphs color listener — any thread. */
    public void onColorChanged(UUID uuid) {
        apply(uuid);
    }

    /** Glyphs title listener — any thread. */
    public void onTitleChanged(UUID uuid) {
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
        NamedTextColor nameColor = glyphs.nameColor(player.getUniqueId())
                .orElse(NamedTextColor.WHITE);
        Component name = formatRow(
                player.getName(),
                nameColor,
                glyphs.equippedTitleText(player.getUniqueId()).orElse(null),
                row == null ? null : row.money,
                row == null ? null : row.glyphs,
                glyphSettings.symbol(),
                glyphSettings.enabled(),
                row == null ? 0L : row.deaths(),
                economy.currencySymbol());
        player.playerListName(name);
    }

    /**
     * {@code [Title] Name  $12.4k  ✦13  ☠29} — compact so wide tabs stay readable.
     */
    static Component formatRow(
            String username,
            NamedTextColor nameColor,
            String titleText,
            Money money,
            Long glyphs,
            String glyphSymbol,
            boolean showGlyphs,
            long deaths,
            String cashSymbol) {
        Component row = Component.empty();
        if (titleText != null && !titleText.isBlank()) {
            row = row.append(Component.text("[", NamedTextColor.GRAY))
                    .append(Component.text(titleText, NamedTextColor.GRAY))
                    .append(Component.text("] ", NamedTextColor.GRAY));
        }
        String cash = money == null
                ? cashSymbol + " —"
                : MoneyHud.formatHud(money, cashSymbol);
        row = row.append(Component.text(username, nameColor))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(cash, NamedTextColor.GREEN));
        if (showGlyphs) {
            String glyphText = glyphs == null
                    ? glyphSymbol + "—"
                    : glyphSymbol + glyphs;
            row = row.append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(glyphText, NamedTextColor.LIGHT_PURPLE));
        }
        return row.append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("☠", NamedTextColor.RED))
                .append(Component.text(deaths, NamedTextColor.RED));
    }

    private static final class Row {
        volatile Money money;
        volatile Long glyphs;
        private final AtomicLong totalDeaths = new AtomicLong(0);
        private final AtomicBoolean baselineReady = new AtomicBoolean(false);

        /** @return true when the visible total changed */
        boolean noteDeath() {
            if (!baselineReady.get()) {
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
