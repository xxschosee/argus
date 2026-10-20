package com.xxschosee.argus.core;

public record Thresholds(
        double warnMspt,
        double criticalMspt,
        double warnTps,
        double criticalTps,
        double warnHeapRatio,
        double criticalHeapRatio,
        int maxEntities,
        int maxChunks,
        int samplesBeforeAlarm
) {

    public Thresholds {
        if (warnMspt <= 0 || criticalMspt <= warnMspt) {
            throw new IllegalArgumentException("MSPT: critical должен быть больше warn");
        }
        if (warnTps <= 0 || criticalTps <= 0 || criticalTps >= warnTps) {
            throw new IllegalArgumentException("TPS: critical должен быть меньше warn");
        }

        if (warnHeapRatio <= 0 || criticalHeapRatio <= 0 || criticalHeapRatio <= warnHeapRatio) {
            throw new IllegalArgumentException("heap ratio: critical должен быть больше warn");
        }
        if (maxEntities <= 0 || maxChunks <= 0) {
            throw new IllegalArgumentException("лимиты должны быть положительными");
        }
        if (samplesBeforeAlarm < 1) {
            throw new IllegalArgumentException("samplesBeforeAlarm должен быть >= 1");
        }
    }

    public static Thresholds defaults() {
        return new Thresholds(
                45.0, 70.0,
                17.0, 12.0,
                0.80, 0.92,
                3000, 4000,
                3
        );
    }
}
