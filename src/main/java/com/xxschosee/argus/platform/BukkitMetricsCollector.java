package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.MetricsSnapshot;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import org.bukkit.Server;
import org.bukkit.World;

public final class BukkitMetricsCollector implements MetricsCollector {

    private final Server server;

    public BukkitMetricsCollector(Server server) {
        this.server = server;
    }

    @Override
    public MetricsSnapshot capture() {
        double[] tps = server.getTPS();
        double tps1m = tps.length > 0 ? tps[0] : 20.0;
        double tps5m = tps.length > 1 ? tps[1] : tps1m;
        double tps15m = tps.length > 2 ? tps[2] : tps5m;

        long heapUsed = 0L;
        long heapMax = 0L;
        MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        if (usage != null) {
            heapUsed = usage.getUsed();
            heapMax = usage.getMax() > 0 ? usage.getMax() : usage.getCommitted();
        }

        int entities = 0;
        int chunks = 0;
        for (World world : server.getWorlds()) {
            entities += world.getEntityCount();
            chunks += world.getLoadedChunks().length;
        }

        return new MetricsSnapshot(
                System.currentTimeMillis(),
                server.getAverageTickTime(),
                tps1m,
                tps5m,
                tps15m,
                heapUsed,
                heapMax,
                server.getOnlinePlayers().size(),
                server.getMaxPlayers(),
                entities,
                chunks
        );
    }
}
