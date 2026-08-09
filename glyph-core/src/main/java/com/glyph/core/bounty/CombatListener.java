package com.glyph.core.bounty;

import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import com.glyph.core.stats.StatType;
import com.glyph.core.stats.StatsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;

/**
 * Records player kills and triggers bounty payouts (GDD sections 25, 33).
 * Runs on the victim's region thread; all persistence is async.
 */
public final class CombatListener implements Listener {

    private final BountyService bounties;
    private final StatsService stats;
    private final SchedulerAdapter scheduler;
    private final EconomySettings economy;
    private final Logger logger;

    public CombatListener(BountyService bounties, StatsService stats,
                          SchedulerAdapter scheduler, EconomySettings economy, Logger logger) {
        this.bounties = bounties;
        this.stats = stats;
        this.scheduler = scheduler;
        this.economy = economy;
        this.logger = logger;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        // Gather everything on the event thread (GDD 65 pattern), then go async.
        Location location = victim.getLocation();
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String weaponJson = weapon.isEmpty() ? null
                : "{\"material\":\"" + weapon.getType().name() + "\"}";
        String cause = victim.getLastDamageCause() != null
                ? victim.getLastDamageCause().getCause().name() : "UNKNOWN";
        String killerName = killer.getName();
        String victimName = victim.getName();

        bounties.recordKill(
                        killer.getUniqueId(), victim.getUniqueId(),
                        location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                        weaponJson, cause)
                .thenAccept(outcome -> {
                    if (outcome.bountyPaid() <= 0) {
                        return;
                    }
                    stats.increment(killer.getUniqueId(), StatType.BOUNTIES_CLAIMED,
                            outcome.bountiesClaimed());
                    String amount = Money.of(outcome.bountyPaid())
                            .format(economy.currencySymbol());
                    // GDD section 33's special bounty message, network-wide.
                    scheduler.runGlobal(() -> Bukkit.getServer().broadcast(Component.text()
                            .append(Component.text(killerName, NamedTextColor.RED))
                            .append(Component.text(" eliminated ", NamedTextColor.GRAY))
                            .append(Component.text(victimName, NamedTextColor.RED))
                            .append(Component.text(" and claimed a ", NamedTextColor.GRAY))
                            .append(Component.text(amount, NamedTextColor.GOLD))
                            .append(Component.text(" bounty.", NamedTextColor.GRAY))
                            .build()));
                })
                .exceptionally(error -> {
                    logger.error("Bounty kill handling failed for {} -> {}",
                            killerName, victimName, error);
                    return null;
                });
    }
}
