package com.xxschosee.argus.core;

/**
 * Чанк-подозреваемый: сколько в нём сущностей и блочных сущностей.
 * Сущности — главный виновник медленных тиков на анархийных серверах.
 */
public record HotChunk(
        String world,
        int chunkX,
        int chunkZ,
        int entityCount,
        int blockEntityCount
) {

    /** «world:12,-8» — короткая запись для сообщений. */
    public String shortName() {
        return world + ":" + chunkX + "," + chunkZ;
    }

    /** Цель для {@link HealAction#chunkTarget}. */
    public String target() {
        return HealAction.chunkTarget(world, chunkX, chunkZ);
    }
}
