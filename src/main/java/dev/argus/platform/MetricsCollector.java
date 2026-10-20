package dev.argus.platform;

import dev.argus.core.MetricsSnapshot;

/** Источник метрик. Позволяет подменить реальный сервер заглушкой в тестах. */
public interface MetricsCollector {

    /** Снять текущий снимок метрик. Вызывается на основном потоке. */
    MetricsSnapshot capture();
}
