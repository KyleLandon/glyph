package com.glyph.core.player;

import com.glyph.api.economy.Money;
import com.glyph.core.config.EconomySettings;
import com.glyph.core.scheduler.SchedulerAdapter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * First-join welcome copy (GDD section 7): anarchy expectations and starter
 * economy pointers. Invoked from {@link PlayerJoinListener} once join
 * persistence completes with {@code firstJoin=true}.
 */
public final class WelcomeListener {

    private static final Duration MESSAGE_DELAY = Duration.ofSeconds(2);

    private final SchedulerAdapter scheduler;
    private final EconomySettings economy;

    public WelcomeListener(SchedulerAdapter scheduler, EconomySettings economy) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /** Called on the async join-completion thread after persistence. */
    public void onPersisted(UUID uuid, boolean firstJoin) {
        if (!firstJoin) {
            return;
        }
        scheduler.runAsyncLater(() -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                return;
            }
            scheduler.runForEntity(player, () -> messages().forEach(player::sendMessage), null);
        }, MESSAGE_DELAY);
    }

    private List<Component> messages() {
        String starter = Money.of(economy.startingBalance()).format(economy.currencySymbol());
        return List.of(
                Component.empty(),
                Component.text("Welcome to GLYPH.", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.empty(),
                Component.text("There are no land claims.", NamedTextColor.GRAY),
                Component.text("There is no grief protection.", NamedTextColor.GRAY),
                Component.text("Your base can be destroyed.", NamedTextColor.RED),
                Component.text("Your items can be stolen.", NamedTextColor.RED),
                Component.empty(),
                Component.text("Trust carefully.", NamedTextColor.YELLOW),
                Component.empty(),
                Component.text("Starter cash: ", NamedTextColor.GRAY)
                        .append(Component.text(starter, NamedTextColor.GREEN))
                        .append(Component.text(" — use /bal, /ah, /bounty, /stats", NamedTextColor.GRAY)),
                Component.text("More: see Discord / website commands list", NamedTextColor.DARK_GRAY),
                Component.empty());
    }
}
