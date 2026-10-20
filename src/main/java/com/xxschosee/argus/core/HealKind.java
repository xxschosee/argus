package com.xxschosee.argus.core;

// Действия с isDestructive() == true выключены по умолчанию.
public enum HealKind {

    NOTIFY_ONLY(false),

    UNLOAD_CHUNK(false),

    DISABLE_AUTOSAVE(false),

    REDUCE_ENTITY_LIMIT(false),

    TRIM_MEMORY(false),

    REMOVE_ENTITIES(true),

    RESTART_SERVER(true);

    private final boolean destructive;

    HealKind(boolean destructive) {
        this.destructive = destructive;
    }

    public boolean isDestructive() {
        return destructive;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

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
