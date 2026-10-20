package com.xxschosee.argus.core;

public record HotChunk(
        String world,
        int chunkX,
        int chunkZ,
        int entityCount,
        int blockEntityCount,
        int playerCount
) {

    public String shortName() {
        return world + ":" + chunkX + "," + chunkZ;
    }

    public String target() {
        return HealAction.chunkTarget(world, chunkX, chunkZ);
    }

    public boolean hasPlayers() {
        return playerCount > 0;
    }

    /**
     * Насколько чанк тяжёлый. Блочные сущности (хопперы, сундуки, редстоун)
     * тяжелее обычных сущностей, поэтому весят больше. Ферма на хопперах
     * без единой мобы раньше в топ не попадала вообще.
     */
    public int weight() {
        return entityCount + blockEntityCount * 2;
    }

    /** Короткое описание для сообщения: что именно нашли. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("сущностей ").append(entityCount);
        if (blockEntityCount > 0) {
            sb.append(", блочных ").append(blockEntityCount);
        }
        if (playerCount > 0) {
            sb.append(", игроков ").append(playerCount);
        }
        return sb.toString();
    }
}
