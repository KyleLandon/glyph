package com.glyph.core.scheduler;

import java.time.Duration;
import java.util.concurrent.Executor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Folia-aware scheduling abstraction (GDD section 40).
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Global state (announcements, aggregate maintenance) → {@link #runGlobal}</li>
 *   <li>Blocks/chunks/region state → {@link #runAtLocation}</li>
 *   <li>Players/entities → {@link #runForEntity}</li>
 *   <li>SQL/Redis/HTTP/serialization → {@link #runAsync} — never on a tick thread</li>
 * </ul>
 */
public interface SchedulerAdapter {

    /** Runs a task on the global region scheduler. */
    void runGlobal(Runnable task);

    /** Runs a task on the global region scheduler after {@code delayTicks}. */
    void runGlobalLater(Runnable task, long delayTicks);

    /** Runs a task on the region that owns {@code location}. */
    void runAtLocation(Location location, Runnable task);

    /**
     * Runs a task on the thread that owns {@code entity}.
     *
     * @param retired invoked instead of {@code task} if the entity is removed
     *                before the task executes; may be {@code null}
     */
    void runForEntity(Entity entity, Runnable task, Runnable retired);

    /** Runs a task on the async scheduler (safe for blocking I/O). */
    void runAsync(Runnable task);

    /** Runs a task on the async scheduler after {@code delay}. */
    void runAsyncLater(Runnable task, Duration delay);

    /** An {@link Executor} view of {@link #runAsync} for CompletableFuture composition. */
    Executor async();
}
