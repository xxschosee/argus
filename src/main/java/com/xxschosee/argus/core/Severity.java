package com.xxschosee.argus.core;

public enum Severity {

    INFO,

    WARNING,

    CRITICAL;

    public int rank() {
        return ordinal();
    }
}
