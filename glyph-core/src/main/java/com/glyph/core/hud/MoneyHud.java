package com.glyph.core.hud;

import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.config.GlyphCurrencySettings;
import com.glyph.core.glyphs.GlyphsService;
import com.glyph.core.scheduler.SchedulerAdapter;
import fr.mrmicky.fastboard.adventure.FastBoard;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Right-side scoreboard sidebar: server title, green money line, light-purple Glyphs line.
 * Shown only when {@code economy.hud.enabled} and the player's {@code glyph_hud_enabled} flag.
 */
public final class MoneyHud implements Listener {

    private final SchedulerAdapter scheduler;
    private final EconomySettings economy;
    private final GlyphCurrencySettings glyphSettings;
    private final GlyphsService glyphs;

    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, Money> balances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> glyphBalances = new ConcurrentHashMap<>();

    public MoneyHud(
            SchedulerAdapter scheduler,
            EconomySettings economy,
            GlyphCurrencySettings glyphSettings,
            GlyphsService glyphs) {
        this.scheduler = scheduler;
        this.economy = economy;
        this.glyphSettings = glyphSettings;
        this.glyphs = glyphs;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!shouldShow(event.getPlayer().getUniqueId())) {
            return;
        }
        scheduler.runForEntity(event.getPlayer(), () -> render(event.getPlayer()), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        FastBoard board = boards.remove(uuid);
        balances.remove(uuid);
        glyphBalances.remove(uuid);
        if (board != null) {
            board.delete();
        }
    }

    /** Called from any thread; registered as an EconomyService balance listener. */
    public void updateBalance(UUID uuid, Money balance) {
        if (!economy.hudEnabled()) {
            return;
        }
        balances.put(uuid, balance);
        refresh(uuid);
    }

    /** Called from any thread; registered as a GlyphsService balance listener. */
    public void updateGlyphs(UUID uuid, Long balance) {
        if (!economy.hudEnabled() || !glyphSettings.enabled()) {
            return;
        }
        glyphBalances.put(uuid, balance);
        refresh(uuid);
    }

    /** Called when the player's HUD preference changes — any thread. */
    public void onHudPreferenceChanged(UUID uuid) {
        if (!economy.hudEnabled()) {
            return;
        }
        if (!glyphs.hudEnabled(uuid)) {
            hide(uuid);
            return;
        }
        refresh(uuid);
    }

    private void hide(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        FastBoard board = boards.remove(uuid);
        if (board != null) {
            board.delete();
        }
        if (player != null) {
            scheduler.runForEntity(player, () -> {
                FastBoard lingering = boards.remove(uuid);
                if (lingering != null) {
                    lingering.delete();
                }
            }, null);
        }
    }

    private boolean shouldShow(UUID uuid) {
        return economy.hudEnabled() && glyphSettings.enabled() && glyphs.hudEnabled(uuid);
    }

    private void refresh(UUID uuid) {
        if (!shouldShow(uuid)) {
            hide(uuid);
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        scheduler.runForEntity(player, () -> render(player), null);
    }

    /** Must run on the player's entity scheduler. */
    private void render(Player player) {
        if (!player.isOnline() || !shouldShow(player.getUniqueId())) {
            hide(player.getUniqueId());
            return;
        }
        UUID uuid = player.getUniqueId();
        FastBoard board = boards.computeIfAbsent(uuid, id -> new FastBoard(player));
        board.updateTitle(Component.text(economy.hudTitle(), NamedTextColor.WHITE));

        Money balance = balances.get(uuid);
        String cashLine = balance == null
                ? economy.currencySymbol() + " —"
                : formatHud(balance, economy.currencySymbol());

        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(cashLine, NamedTextColor.GREEN));
        Long glyphBalance = glyphBalances.get(uuid);
        String glyphLine = glyphBalance == null
                ? glyphSettings.symbol() + " —"
                : formatGlyphs(glyphBalance, glyphSettings.symbol());
        lines.add(Component.text(glyphLine, NamedTextColor.LIGHT_PURPLE));
        board.updateLines(lines, List.of(Component.empty()));
    }

    /**
     * Compact cash line like {@code $ 1.6M} / {@code $ 12K} / {@code $ 100}.
     * Space after the symbol matches the common SMP look.
     */
    static String formatHud(Money balance, String symbol) {
        long dollars = balance.dollars();
        if (dollars >= 1_000_000_000L) {
            return symbol + " " + compact(dollars / 1_000_000_000.0) + "B";
        }
        if (dollars >= 1_000_000L) {
            return symbol + " " + compact(dollars / 1_000_000.0) + "M";
        }
        if (dollars >= 10_000L) {
            return symbol + " " + compact(dollars / 1_000.0) + "K";
        }
        return symbol + " " + String.format(Locale.US, "%,d", dollars);
    }

    static String formatGlyphs(long amount, String symbol) {
        return symbol + " " + String.format(Locale.US, "%,d", amount);
    }

    /** {@code 1.6} not {@code 1.60}; whole numbers drop the decimal. */
    private static String compact(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
