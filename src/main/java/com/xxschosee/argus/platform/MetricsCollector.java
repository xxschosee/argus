package com.xxschosee.argus.platform;

import com.xxschosee.argus.core.MetricsSnapshot;

public interface MetricsCollector {

    MetricsSnapshot capture();
}
