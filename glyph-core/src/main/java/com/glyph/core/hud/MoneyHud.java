package com.glyph.core.hud;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
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
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.slf4j.Logger;

/**
 * Right-side cash HUD (DonutSMP-style): scoreboard sidebar with the server
 * name as the title and a single green money line under it.
 *
 * <p>Updates are event-driven from
 * {@link com.glyph.core.economy.EconomyService#addBalanceListener} — no
 * polling. Folia safety: scoreboards are only touched on the owning player's
 * entity scheduler.</p>
 *
 * <p>Client minimaps default to the top-right and can cover this. Players
 * should park Xaero (etc.) on the left: {@code Y → Change Position}.</p>
 */
public final class MoneyHud implements Listener {

    private static final String OBJECTIVE_NAME = "glyph_money";

    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;
    private final EconomyApi economy;
    private final Logger logger;

    /** Current sidebar money entry per player (needed to reset the old line). */
    private final Map<UUID, String> currentLines = new ConcurrentHashMap<>();

    public MoneyHud(
            SchedulerAdapter scheduler,
            EconomySettings settings,
            EconomyApi economy,
            Logger logger) {
        this.scheduler = scheduler;
        this.settings = settings;
        this.economy = economy;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!settings.hudEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        render(player, null);
        economy.balance(uuid).whenComplete((balance, error) -> {
            if (error != null) {
                logger.debug("Money HUD: initial balance fetch failed for {}", uuid, error);
            } else {
                balance.ifPresent(value -> updateBalance(uuid, value));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        currentLines.remove(event.getPlayer().getUniqueId());
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
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            // White server name on top — matches the DonutSMP layout.
            objective = board.registerNewObjective(
                    OBJECTIVE_NAME,
                    Criteria.DUMMY,
                    Component.text(settings.hudTitle(), NamedTextColor.WHITE));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            // Hide the red score digits on the right of each line.
            objective.numberFormat(NumberFormat.blank());
            player.setScoreboard(board);
        } else {
            objective.displayName(Component.text(settings.hudTitle(), NamedTextColor.WHITE));
        }

        String line = "§a" + (balance == null
                ? settings.currencySymbol() + " —"
                : formatHud(balance, settings.currencySymbol()));

        String previous = currentLines.put(player.getUniqueId(), line);
        if (line.equals(previous)) {
            return;
        }
        if (previous != null) {
            board.resetScores(previous);
        }
        objective.getScore(line).setScore(0);
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
