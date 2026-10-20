package com.xxschosee.argus.platform;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import com.xxschosee.argus.core.TickTimer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Измеряет, сколько времени занял тик.
 *
 * Важно: длительность тика это время от начала до конца одного тика, а не
 * интервал между началами тиков. Интервал включает в себя ожидание сервера
 * между тиками, и на здоровом сервере он равен 50 мс при 20 TPS, даже если
 * сам тик отработал за 2 мс. Мерить надо по ServerTickEndEvent.
 */
public final class TickListener implements Listener {

    private final TickTimer timer;
    private long tickStartNanos;
    private volatile Thread mainThread;

    public TickListener(TickTimer timer) {
        this.timer = timer;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickStart(ServerTickStartEvent event) {
        tickStartNanos = System.nanoTime();
        mainThread = Thread.currentThread();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ServerTickEndEvent event) {
        if (tickStartNanos != 0L) {
            timer.record(System.nanoTime() - tickStartNanos);
            tickStartNanos = 0L;
        }
    }

    /** Главный поток сервера: он же нужен для сэмплирования стека. */
    public Thread mainThread() {
        return mainThread;
    }
}
