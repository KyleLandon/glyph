package com.glyph.core.hud;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * FiveM-style money HUD: a scoreboard sidebar showing the player's cash,
 * updated the moment a balance changes (via
 * {@link com.glyph.core.economy.EconomyService#addBalanceListener}) — no
 * polling, no database reads beyond the initial join fetch.
 *
 * <p>Folia safety: scoreboards are only touched on the owning player's entity
 * scheduler. Balance change notifications arrive on async I/O threads and are
 * bounced to the right thread here.</p>
 */
public final class MoneyHud implements Listener {

    private static final String OBJECTIVE_NAME = "glyph_money";

    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;
    private final EconomyApi economy;
    private final Logger logger;

    /** Current sidebar line per player; needed to reset the old score entry. */
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

        // Placeholder immediately (join events run on the player's region
        // thread), real value once the async balance fetch completes.
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
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY,
                    Component.text(settings.hudTitle(), NamedTextColor.GOLD, TextDecoration.BOLD));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
            player.setScoreboard(board);
        }

        String line = "§a§l" + (balance == null
                ? settings.currencySymbol() + "—"
                : balance.format(settings.currencySymbol()));

        String previous = currentLines.put(player.getUniqueId(), line);
        if (line.equals(previous)) {
            return;
        }
        if (previous != null) {
            board.resetScores(previous);
        }
        objective.getScore(line).setScore(0);
    }
}
