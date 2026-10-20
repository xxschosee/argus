package com.xxschosee.argus.core;

public record HotChunk(
        String world,
        int chunkX,
        int chunkZ,
        int entityCount,
        int blockEntityCount
) {

    public String shortName() {
        return world + ":" + chunkX + "," + chunkZ;
    }

    public String target() {
        return HealAction.chunkTarget(world, chunkX, chunkZ);
    }
}
