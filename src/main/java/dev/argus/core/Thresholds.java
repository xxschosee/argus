package dev.argus.core;

/**
 * Пороги, при которых состояние сервера считается деградировавшим.
 * Значения приходят из config.yml и валидируются при загрузке плагина.
 *
 * @param warnMspt          порог предупреждения по времени тика, мс
 * @param criticalMspt      критический порог по времени тика, мс
 * @param warnTps           порог предупреждения по TPS (ниже — плохо)
 * @param criticalTps       критический порог по TPS
 * @param warnHeapRatio     порог предупреждения по доле кучи, 0..1
 * @param criticalHeapRatio критический порог по доле кучи, 0..1
 * @param maxEntities       предел нормального числа загруженных сущностей
 * @param maxChunks         предел нормального числа загруженных чанков
 * @param samplesBeforeAlarm сколько подряд идущих снимков должно быть плохо,
 *                           прежде чем сработает тревога (защита от одиночных всплесков)
 */
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
        // Для кучи «хуже» = «больше», поэтому critical обязан быть ВЫШЕ warn.
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
