package com.xxschosee.argus.core;

/**
 * Неизменяемый снимок метрик сервера в один момент времени.
 * Собирается адаптером платформы, потребляется чистым ядром.
 * Ядро не знает, откуда пришли цифры, — это позволяет тестировать его без сервера.
 */
public record MetricsSnapshot(
        long capturedAtMillis,
        double mspt,
        double tps1m,
        double tps5m,
        double tps15m,
        long heapUsedBytes,
        long heapMaxBytes,
        int playersOnline,
        int playersMax,
        int entitiesLoaded,
        int chunksLoaded
) {

    /** Доля занятой кучи, 0..1. При неизвестном лимите — 0. */
    public double heapRatio() {
        if (heapMaxBytes <= 0) {
            return 0.0;
        }
        return (double) heapUsedBytes / (double) heapMaxBytes;
    }

    /** Загрузка относительно лимита игроков, 0..1. */
    public double playerRatio() {
        if (playersMax <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) playersOnline / (double) playersMax);
    }

    /** TPS никогда не опускается ниже 0.1 в API, но страхуемся от нуля в делении. */
    public double safeTps() {
        return Math.max(0.0, tps1m);
    }
}
