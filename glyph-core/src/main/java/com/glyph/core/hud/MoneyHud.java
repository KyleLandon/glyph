package com.glyph.core.hud;

import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import fr.mrmicky.fastboard.adventure.FastBoard;
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
 * Right-side cash HUD (DonutSMP-style): packet scoreboard sidebar with the
 * server name as the title and a single green money line under it.
 *
 * <p>Uses FastBoard instead of Bukkit {@code ScoreboardManager} — Folia throws
 * {@code UnsupportedOperationException} on {@code getNewScoreboard()}.</p>
 *
 * <p>Updates are event-driven from
 * {@link com.glyph.core.economy.EconomyService#addBalanceListener} — including
 * a post-join {@code resyncBalance} after the starting-balance mint. Folia
 * safety: boards are only touched on the owning player's entity scheduler.</p>
 *
 * <p>Client minimaps default to the top-right and can cover this. Players
 * should park Xaero (etc.) on the left: {@code Y → Change Position}.</p>
 */
public final class MoneyHud implements Listener {

    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;

    private final Map<UUID, FastBoard> boards = new ConcurrentHashMap<>();

    public MoneyHud(SchedulerAdapter scheduler, EconomySettings settings) {
        this.scheduler = scheduler;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.hudEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        // Placeholder until join persistence finishes and EconomyService resyncs.
        scheduler.runForEntity(player, () -> render(player, null), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        FastBoard board = boards.remove(event.getPlayer().getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    /** Called from any thread; also registered as an EconomyService balance listener. */
    public void updateBalance(UUID uuid, Money balance) {
        if (!settings.hudEnabled()) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        scheduler.runForEntity(player, () -> render(player, balance), null);
    }

    /** Must run on the player's entity scheduler. */
    private void render(Player player, Money balance) {
        if (!player.isOnline()) {
            return;
        }
        FastBoard board = boards.computeIfAbsent(player.getUniqueId(), id -> new FastBoard(player));
        board.updateTitle(Component.text(settings.hudTitle(), NamedTextColor.WHITE));

        String line = balance == null
                ? settings.currencySymbol() + " —"
                : formatHud(balance, settings.currencySymbol());
        // Blank custom scores so the red digits on the right don't show.
        board.updateLines(
                List.of(Component.text(line, NamedTextColor.GREEN)),
                List.of(Component.empty()));
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
            // Start abbreviating at 10K so four-digit balances stay readable.
            return symbol + " " + compact(dollars / 1_000.0) + "K";
        }
        return symbol + " " + String.format(Locale.US, "%,d", dollars);
    }

    /** {@code 1.6} not {@code 1.60}; whole numbers drop the decimal. */
    private static String compact(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.1f", value);
    }
}
