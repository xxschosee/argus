package dev.argus.core;

/** Что именно плагин может сделать с проблемой. */
public enum HealKind {

    /** Только сообщить админу, ничего не менять. */
    NOTIFY_ONLY(false),

    /** Выгрузить чанк из памяти. Обратимо: чанк перезагрузится по требованию. */
    UNLOAD_CHUNK(false),

    /** Временно отключить автосохранение. Обратимо, но игроки могут потерять прогресс. */
    DISABLE_AUTOSAVE(false),

    /** Снизить лимит сущностей на чанк. Обратимо, но меняет геймплей. */
    REDUCE_ENTITY_LIMIT(false),

    /** Принудительная сборка мусора. Обратимо, но вызывает паузу. */
    TRIM_MEMORY(false),

    /** Удалить сущности из чанка. Разрушительно: часть игроков теряет мобов/предметы. */
    REMOVE_ENTITIES(true),

    /** Перезапустить сервер через панель. Разрушительно: все игроки отваляются. */
    RESTART_SERVER(true);

    private final boolean destructive;

    HealKind(boolean destructive) {
        this.destructive = destructive;
    }

    /** Разрушительные действия по умолчанию выключены в конфиге. */
    public boolean isDestructive() {
        return destructive;
    }

    /** Ключ для конфига: healer.actions.&lt;cause&gt; = [notify_only, unload_chunk]. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Разобрать ключ из конфига; неизвестное значение — {@link #NOTIFY_ONLY}. */
    public static HealKind fromKey(String key) {
        if (key == null) {
            return NOTIFY_ONLY;
        }
        String normalized = key.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        for (HealKind kind : values()) {
            if (kind.key().equals(normalized)) {
                return kind;
            }
        }
        return NOTIFY_ONLY;
    }
}
