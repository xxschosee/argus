package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.HealAction;
import com.xxschosee.argus.core.HealKind;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Применяет лечебные действия на Paper-сервере.
 *
 * Все действия, кроме {@link HealKind#REMOVE_ENTITIES}, обратимы: выгруженный чанк
 * вернётся по требованию, автосохранение включится обратно по таймеру.
 */
public final class BukkitHealExecutor implements HealExecutor {

    private final Plugin plugin;
    private final Logger logger;
    private final long autosaveRestoreTicks;

    public BukkitHealExecutor(Plugin plugin, Logger logger, long autosaveRestoreTicks) {
        this.plugin = plugin;
        this.logger = logger;
        this.autosaveRestoreTicks = autosaveRestoreTicks;
    }

    @Override
    public boolean apply(HealAction action) {
        return switch (action.kind()) {
            case NOTIFY_ONLY -> false; // это не действие, а уведомление
            case TRIM_MEMORY -> trimMemory();
            case UNLOAD_CHUNK -> unloadChunk(action.target());
            case REMOVE_ENTITIES -> removeEntities(action.target());
            case DISABLE_AUTOSAVE -> disableAutosave();
            case REDUCE_ENTITY_LIMIT -> unsupported(action, "лимит сущностей на чанк не меняется через API");
            case RESTART_SERVER -> unsupported(action, "рестарт через панель появится в следующей версии");
        };
    }

    @Override
    public String name() {
        return "bukkit";
    }

    // ------------------------------------------------------------------

    private boolean trimMemory() {
        long before = usedHeap();
        System.gc();
        logger.info("Принудительная сборка мусора: куча "
                + (before / 1024 / 1024) + " МБ -> " + (usedHeap() / 1024 / 1024) + " МБ");
        return true;
    }

    private boolean unloadChunk(String target) {
        ChunkRef ref = ChunkRef.parse(target);
        if (ref == null) {
            logger.warning("Не удалось разобрать цель выгрузки: " + target);
            return false;
        }
        World world = Bukkit.getWorld(ref.world());
        if (world == null) {
            logger.warning("Мир не найден: " + ref.world());
            return false;
        }
        // unloadChunkRequest уважает присутствие игроков и не выгружает чанк насильно
        boolean requested = world.unloadChunkRequest(ref.x(), ref.z());
        if (requested) {
            logger.info("Запрошена выгрузка чанка " + ref.shortName());
        } else {
            logger.warning("Сервер отклонил выгрузку чанка " + ref.shortName());
        }
        return requested;
    }

    private boolean removeEntities(String target) {
        ChunkRef ref = ChunkRef.parse(target);
        if (ref == null) {
            logger.warning("Не удалось разобрать цель очистки: " + target);
            return false;
        }
        World world = Bukkit.getWorld(ref.world());
        if (world == null) {
            return false;
        }
        Chunk chunk = findLoadedChunk(world, ref.x(), ref.z());
        if (chunk == null) {
            return false;
        }
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Player) {
                continue;
            }
            if (entity.isValid()) {
                entity.remove();
                removed++;
            }
        }
        logger.info("Удалено сущностей в " + ref.shortName() + ": " + removed);
        return true;
    }

    private boolean disableAutosave() {
        int changed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (world.isAutoSave()) {
                world.setAutoSave(false);
                changed++;
            }
        }
        if (changed == 0) {
            return false;
        }
        logger.warning("Автосохранение отключено в " + changed + " мирах на "
                + (autosaveRestoreTicks / 20) + " с");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                world.setAutoSave(true);
            }
            logger.info("Автосохранение включено обратно");
        }, autosaveRestoreTicks);
        return true;
    }

    private boolean unsupported(HealAction action, String reason) {
        logger.warning("Действие " + action.kind().key() + " не поддержано: " + reason);
        return false;
    }

    private long usedHeap() {
        MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return usage == null ? 0L : usage.getUsed();
    }

    /** Найти уже загруженный чанк, не вызывая getChunkAt (тот загрузил бы его). */
    private Chunk findLoadedChunk(World world, int x, int z) {
        for (Chunk chunk : world.getLoadedChunks()) {
            if (chunk.getX() == x && chunk.getZ() == z) {
                return chunk;
            }
        }
        return null;
    }

    /** Разобранная цель вида {@code chunk:world:12,-8}. */
    record ChunkRef(String world, int x, int z) {

        static ChunkRef parse(String target) {
            if (target == null) {
                return null;
            }
            String[] parts = target.split(":");
            if (parts.length != 3 || !"chunk".equals(parts[0])) {
                return null;
            }
            String[] coords = parts[2].split(",");
            if (coords.length != 2) {
                return null;
            }
            try {
                return new ChunkRef(parts[1],
                        Integer.parseInt(coords[0].trim()),
                        Integer.parseInt(coords[1].trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String shortName() {
            return world + ":" + x + "," + z;
        }
    }
}
