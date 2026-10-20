package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.ChunkChurn;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/** Считает, сколько чанков сервер грузит и выгружает. */
public final class ChunkListener implements Listener {

    private final ChunkChurn churn;

    public ChunkListener(ChunkChurn churn) {
        this.churn = churn;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        churn.onLoad();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        churn.onUnload();
    }
}
