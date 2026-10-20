package com.xxschosee.argus.core;

/**
 * Сколько времени сервер тратит на сборку мусора.
 *
 * Считается по разнице между двумя замерами: если сборщик мусора забирает
 * заметную долю времени, лаг вызван памятью, а не плагином.
 */
public final class GcStats {

    private final double sampleIntervalSeconds;
    private long lastCollectionCount;
    private long lastCollectionMillis;
    private long lastSampleAtMillis;
    private double lastOverhead;

    public GcStats(double sampleIntervalSeconds) {
        this.sampleIntervalSeconds = Math.max(1.0, sampleIntervalSeconds);
    }

    public void sample(long collectionCount, long collectionMillis, long now) {
        if (lastSampleAtMillis == 0L) {
            lastCollectionCount = collectionCount;
            lastCollectionMillis = collectionMillis;
            lastSampleAtMillis = now;
            return;
        }
        long elapsed = now - lastSampleAtMillis;
        if (elapsed < 1000L) {
            return;
        }
        long millis = collectionMillis - lastCollectionMillis;
        lastOverhead = elapsed > 0 ? millis * 100.0 / elapsed : 0.0;
        lastCollectionCount = collectionCount;
        lastCollectionMillis = collectionMillis;
        lastSampleAtMillis = now;
    }

    /** Доля времени в сборке мусора, процентами. */
    public double overheadPercent() {
        return lastOverhead;
    }

    public long collectionCount() {
        return lastCollectionCount;
    }

    /** Порог, выше которого лаг считается вызванным памятью. */
    public boolean isMemoryBound(double warnPercent) {
        return lastOverhead >= warnPercent;
    }
}
