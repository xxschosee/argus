package com.xxschosee.argus.core;

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

    public double heapRatio() {
        if (heapMaxBytes <= 0) {
            return 0.0;
        }
        return (double) heapUsedBytes / (double) heapMaxBytes;
    }

    public double playerRatio() {
        if (playersMax <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) playersOnline / (double) playersMax);
    }

    public double safeTps() {
        return Math.max(0.0, tps1m);
    }
}
