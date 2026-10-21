package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.HotSpotScanner;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class BukkitChunkSource implements HotSpotScanner.ChunkSource {

    private final Server server;
    private List<Chunk> chunks = List.of();

    public BukkitChunkSource(Server server) {
        this.server = server;
    }

    @Override
    public void refresh() {
        // Сначала идут чанки с игроками: виновник почти всегда рядом с людьми,
        // и так поиск заканчивается за пару тиков вместо десятков.
        List<Chunk> ordered = new ArrayList<>();
        Set<Chunk> seen = new HashSet<>();
        for (Player player : server.getOnlinePlayers()) {
            Chunk chunk = player.getChunk();
            if (seen.add(chunk)) {
                ordered.add(chunk);
            }
        }
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (seen.add(chunk)) {
                    ordered.add(chunk);
                }
            }
        }
        this.chunks = List.copyOf(ordered);
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
        return new BukkitProbe(chunks.get(index));
    }

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
            // getEntities у Paper подгружает сущности чанка, если они не лежат в памяти.
            // Считать их силой значит самому вызывать подгрузку всех чанков мира.
            if (!chunk.isEntitiesLoaded()) {
                return 0;
            }
            return chunk.getEntities().length;
        }

        @Override
        public int blockEntityCount() {
            return chunk.getTileEntities(false).length;
        }

        @Override
        public int playerCount() {
            int players = 0;
            for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                if (entity instanceof Player) {
                    players++;
                }
            }
            return players;
        }
    }
}
