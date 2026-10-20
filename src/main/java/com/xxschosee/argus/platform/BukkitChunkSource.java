package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.HotSpotScanner;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * Источник загруженных чанков сервера.
 *
 * Список пересобирается один раз на цикл скана, дальше чанки опрашиваются по индексу.
 * Дорогая операция — подсчёт сущностей — вызывается только из пошагового скана,
 * поэтому лага самому себе скан не создаёт.
 */
public final class BukkitChunkSource implements HotSpotScanner.ChunkSource {

    private final Server server;
    private List<Chunk> chunks = List.of();

    public BukkitChunkSource(Server server) {
        this.server = server;
    }

    @Override
    public void refresh() {
        List<Chunk> collected = new ArrayList<>();
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                collected.add(chunk);
            }
        }
        this.chunks = List.copyOf(collected);
    }

    @Override
    public int size() {
        return chunks.size();
    }

    @Override
    public HotSpotScanner.ChunkProbe probe(int index) {
        if (index < 0 || index >= chunks.size()) {
            return null;
        }
        Chunk chunk = chunks.get(index);
        return new BukkitProbe(chunk);
    }

    /** Ленивый опрос одного чанка: счётчики читаются в момент вызова. */
    private record BukkitProbe(Chunk chunk) implements HotSpotScanner.ChunkProbe {

        @Override
        public String world() {
            return chunk.getWorld().getName();
        }

        @Override
        public int chunkX() {
            return chunk.getX();
        }

        @Override
        public int chunkZ() {
            return chunk.getZ();
        }

        @Override
        public int entityCount() {
            return chunk.getEntities().length;
        }

        @Override
        public int blockEntityCount() {
            return chunk.getTileEntities(false).length;
        }
    }
}
