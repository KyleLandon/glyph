package com.glyph.core.command;

import com.glyph.core.scheduler.SchedulerAdapter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.slf4j.Logger;

/**
 * {@code /restart} — 10-second chat countdown, then Paper's restart script
 * (so Folia comes back up with {@code plugins/update} jars).
 */
public final class RestartCommand implements CommandExecutor, TabCompleter {

    public static final int COUNTDOWN_SECONDS = 10;

    private final SchedulerAdapter scheduler;
    private final Logger logger;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    public RestartCommand(SchedulerAdapter scheduler, Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        startCountdown(sender);
        return true;
    }

    public void startCountdown(CommandSender initiator) {
        if (!inProgress.compareAndSet(false, true)) {
            initiator.sendMessage(Component.text(
                    "A restart is already in progress.", NamedTextColor.RED));
            return;
        }
        logger.info("Restart requested by {}", initiator.getName());
        Bukkit.getServer().broadcast(announcement(COUNTDOWN_SECONDS, initiator.getName()));
        for (int remaining = COUNTDOWN_SECONDS - 1; remaining >= 1; remaining--) {
            int seconds = remaining;
            scheduler.runGlobalLater(
                    () -> Bukkit.getServer().broadcast(announcement(seconds, null)),
                    (COUNTDOWN_SECONDS - remaining) * 20L);
        }
        scheduler.runGlobalLater(this::performRestart, COUNTDOWN_SECONDS * 20L);
    }

    static Component announcement(int seconds, String initiator) {
        if (initiator != null) {
            return Component.text(initiator, NamedTextColor.GOLD)
                    .append(Component.text(" is restarting the server in ", NamedTextColor.YELLOW))
                    .append(Component.text(seconds + " seconds.", NamedTextColor.GOLD));
        }
        if (seconds == 1) {
            return Component.text("Restarting in 1 second.", NamedTextColor.GOLD);
        }
        return Component.text("Restarting in " + seconds + " seconds.", NamedTextColor.GOLD);
    }

    private void performRestart() {
        Bukkit.getServer().broadcast(Component.text("Restarting now.", NamedTextColor.RED));
        File script = new File("restart-server.bat");
        if (script.isFile()) {
            try {
                new ProcessBuilder(
                                "cmd.exe", "/c", "start", "\"Glyph Restart\"", "/MIN",
                                script.getAbsolutePath())
                        .directory(script.getAbsoluteFile().getParentFile())
                        .start();
                logger.info("Launched {}", script.getAbsolutePath());
            } catch (IOException error) {
                logger.error("Could not launch restart-server.bat — shutting down only", error);
            }
        } else {
            logger.warn("restart-server.bat missing in {} — shutting down only",
                    new File(".").getAbsolutePath());
        }
        Bukkit.shutdown();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        return List.of();
    }
}
