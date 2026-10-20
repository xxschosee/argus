package com.xxschosee.argus.platform;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.xxschosee.argus.core.TickTimer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Измеряет длительность каждого тика.
 *
 * Разница между двумя соседними началами тиков и есть время, которое
 * сервер потратил на тик. Это точнее getAverageTickTime(): там усреднение
 * по окну, и одиночный всплеск в 300 мс теряется.
 */
public final class TickListener implements Listener {

    private final TickTimer timer;
    private long lastStartNanos;

    public TickListener(TickTimer timer) {
        this.timer = timer;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickStart(ServerTickStartEvent event) {
        long now = System.nanoTime();
        if (lastStartNanos != 0L) {
            timer.record(now - lastStartNanos);
        }
        lastStartNanos = now;
    }
}
