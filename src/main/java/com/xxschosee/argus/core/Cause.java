package com.xxschosee.argus.core;

public enum Cause {

    TPS_DEGRADED,

    HIGH_MSPT,

    HEAP_PRESSURE,

    ENTITY_OVERLOAD,

    CHUNK_OVERLOAD;

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
