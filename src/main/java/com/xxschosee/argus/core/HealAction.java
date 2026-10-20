package com.xxschosee.argus.core;

public record HealAction(HealKind kind, String target, String description) {

    public HealAction {
        if (kind == null) {
            throw new IllegalArgumentException("kind обязателен");
        }
        if (target == null) {
            target = "";
        }
        if (description == null) {
            description = kind.key();
        }
    }

    public static HealAction global(HealKind kind, String description) {
        return new HealAction(kind, "", description);
    }

    public boolean isDestructive() {
        return kind.isDestructive();
    }

    public static String chunkTarget(String world, int x, int z) {
        return "chunk:" + world + ":" + x + "," + z;
    }
}
