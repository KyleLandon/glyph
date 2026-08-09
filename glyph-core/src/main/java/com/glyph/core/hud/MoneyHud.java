package com.glyph.core.hud;

import com.glyph.api.economy.EconomyApi;
import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.slf4j.Logger;

/**
 * Cash HUD shown on the action bar (above the hotbar).
 *
 * <p>The earlier scoreboard-sidebar approach sat under Xaero's Minimap (and
 * most other minimap mods), so players never saw their balance. The action
 * bar is bottom-center and stays clear. It fades after a couple of seconds
 * unless resent, so each player gets a light entity-scheduler refresh.</p>
 *
 * <p>Folia safety: action bars and entity tasks only run on the owning
 * player's entity scheduler. Balance notifications arrive on async I/O
 * threads and are bounced here.</p>
 */
public final class MoneyHud implements Listener {

    /** Legacy sidebar objective — cleared on join so old boards disappear. */
    private static final String LEGACY_OBJECTIVE = "glyph_money";

    /** Re-send interval so the action bar does not fade away. */
    private static final long REFRESH_TICKS = 40L;

    private final Plugin plugin;
    private final SchedulerAdapter scheduler;
    private final EconomySettings settings;
    private final EconomyApi economy;
    private final Logger logger;

    private final Map<UUID, Money> balances = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> refreshTasks = new ConcurrentHashMap<>();

    public MoneyHud(
            Plugin plugin,
            SchedulerAdapter scheduler,
            EconomySettings settings,
            EconomyApi economy,
            Logger logger) {
        this.plugin = plugin;
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
        clearLegacySidebar(player);
        show(player, null);
        startRefresh(player);

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
        UUID uuid = event.getPlayer().getUniqueId();
        balances.remove(uuid);
        ScheduledTask task = refreshTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /** Called from any thread; also registered as an EconomyService balance listener. */
    public void updateBalance(UUID uuid, Money balance) {
        if (!settings.hudEnabled()) {
            return;
        }
        balances.put(uuid, balance);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        scheduler.runForEntity(player, () -> show(player, balance), null);
    }

    private void startRefresh(Player player) {
        UUID uuid = player.getUniqueId();
        ScheduledTask previous = refreshTasks.remove(uuid);
        if (previous != null) {
            previous.cancel();
        }
        ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduled -> show(player, balances.get(uuid)),
                () -> refreshTasks.remove(uuid),
                REFRESH_TICKS,
                REFRESH_TICKS);
        if (task != null) {
            refreshTasks.put(uuid, task);
        }
    }

    /** Must run on the player's entity scheduler. */
    private void show(Player player, Money balance) {
        String text = balance == null
                ? settings.currencySymbol() + "—"
                : balance.format(settings.currencySymbol());
        player.sendActionBar(Component.text(text, NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    /** Drop the old sidebar HUD if a previous build left one on this player. */
    private static void clearLegacySidebar(Player player) {
        Objective objective = player.getScoreboard().getObjective(LEGACY_OBJECTIVE);
        if (objective != null) {
            if (objective.getDisplaySlot() == DisplaySlot.SIDEBAR) {
                objective.setDisplaySlot(null);
            }
            objective.unregister();
        }
    }
}
