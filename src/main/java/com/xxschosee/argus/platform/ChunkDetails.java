package com.xxschosee.argus.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;

/**
 * Разбирает содержимое конкретного чанка: каких сущностей и блочных сущностей
 * в нём больше всего.
 *
 * Считается только для подозреваемого чанка, а не для всех, потому что обход
 * содержимого дорогой. Админу это отвечает на вопрос "что именно удалять":
 * две тысячи зомби, сто хопперов и полтора десятка сундуков требуют
 * совершенно разных действий.
 */
public final class ChunkDetails {

    private ChunkDetails() {
    }

    public static String describe(String worldName, int chunkX, int chunkZ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return "мир не найден";
        }
        Chunk chunk = null;
        for (Chunk loaded : world.getLoadedChunks()) {
            if (loaded.getX() == chunkX && loaded.getZ() == chunkZ) {
                chunk = loaded;
                break;
            }
        }
        if (chunk == null) {
            return "чанк не загружен";
        }

        Map<String, Integer> entities = new LinkedHashMap<>();
        for (Entity entity : chunk.getEntities()) {
            entities.merge(entity.getType().name(), 1, Integer::sum);
        }
        Map<String, Integer> blocks = new LinkedHashMap<>();
        for (BlockState state : chunk.getTileEntities(false)) {
            blocks.merge(state.getType().name(), 1, Integer::sum);
        }
        return "сущности: " + top(entities) + " | блочные: " + top(blocks);
    }

    private static String top(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "нет";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (shown++ >= 3) {
                sb.append(", ещё ").append(entries.size() - 3).append(" видов");
                break;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(' ').append(entry.getValue());
        }
        return sb.toString();
    }
}
