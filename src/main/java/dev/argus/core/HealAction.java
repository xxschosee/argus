package dev.argus.core;

/**
 * Одно конкретное действие: что сделать и с чем.
 *
 * @param kind        вид действия
 * @param target      цель: {@code chunk:world:12,-8} для чанковых действий,
 *                    пустая строка для глобальных
 * @param description человекочитаемое описание — попадёт прямо в сообщение в Telegram
 */
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

    /** Цель в виде {@code world:x,z} для чанковых действий. */
    public static String chunkTarget(String world, int x, int z) {
        return "chunk:" + world + ":" + x + "," + z;
    }
}
