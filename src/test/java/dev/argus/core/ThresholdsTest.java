package dev.argus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThresholdsTest {

    @Test
    @DisplayName("Пороги по умолчанию валидны")
    void defaultsAreValid() {
        Thresholds t = Thresholds.defaults();
        assertTrue(t.warnMspt() < t.criticalMspt());
        assertTrue(t.criticalTps() < t.warnTps());
        assertTrue(t.warnHeapRatio() < t.criticalHeapRatio());
    }

    @Test
    @DisplayName("critical MSPT должен быть больше warn")
    void rejectsInvertedMspt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(70.0, 45.0, 17.0, 12.0, 0.8, 0.9, 100, 100, 1));
    }

    @Test
    @DisplayName("critical TPS должен быть меньше warn")
    void rejectsInvertedTps() {
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(45.0, 70.0, 12.0, 17.0, 0.8, 0.9, 100, 100, 1));
    }

    @Test
    @DisplayName("Порог кучи не может быть выше критического")
    void rejectsInvertedHeapRatio() {
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(45.0, 70.0, 17.0, 12.0, 0.95, 0.9, 100, 100, 1));
    }

    @Test
    @DisplayName("Нулевые лимиты недопустимы")
    void rejectsZeroLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(45.0, 70.0, 17.0, 12.0, 0.8, 0.9, 0, 100, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(45.0, 70.0, 17.0, 12.0, 0.8, 0.9, 100, 0, 1));
    }

    @Test
    @DisplayName("Для тревоги нужен минимум один замер")
    void rejectsZeroSamples() {
        assertThrows(IllegalArgumentException.class,
                () -> new Thresholds(45.0, 70.0, 17.0, 12.0, 0.8, 0.9, 100, 100, 0));
    }

    @Test
    @DisplayName("Доля кучи считается корректно и не падает при нулевом лимите")
    void heapRatioIsSafe() {
        MetricsSnapshot normal = new MetricsSnapshot(1L, 12.0, 20.0, 20.0, 20.0,
                512L * 1024 * 1024, 1024L * 1024 * 1024, 0, 0, 0, 0);
        assertEquals(0.5, normal.heapRatio(), 0.0001);

        MetricsSnapshot noLimit = new MetricsSnapshot(1L, 12.0, 20.0, 20.0, 20.0,
                512L * 1024 * 1024, 0L, 0, 0, 0, 0);
        assertEquals(0.0, noLimit.heapRatio(), 0.0001);
    }

    @Test
    @DisplayName("Лимиты не могут быть неположительными")
    void rejectsNegativeLimits() {
        assertFalse(Thresholds.defaults().maxEntities() <= 0);
    }
}
