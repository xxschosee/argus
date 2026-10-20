package com.xxschosee.argus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IncidentLogTest {

    @Test
    @DisplayName("Журнал хранит события, а recent() отдаёт свежие сверху")
    void keepsOrder() {
        IncidentLog log = new IncidentLog(10);
        log.record(Diagnosis.problem(Cause.TPS_DEGRADED, Severity.WARNING, "a", Map.of()), 1L);
        log.record(Diagnosis.problem(Cause.HEAP_PRESSURE, Severity.CRITICAL, "b", Map.of()), 2L);

        List<IncidentLog.Entry> recent = log.recent(10);
        assertEquals(2, recent.size());
        assertEquals(Cause.HEAP_PRESSURE, recent.get(0).cause(), "свежее событие идёт первым");
        assertEquals(Cause.TPS_DEGRADED, recent.get(1).cause());
    }

    @Test
    @DisplayName("Журнал ограничен по размеру: старые события вытесняются")
    void keepsOnlyLastEntries() {
        IncidentLog log = new IncidentLog(3);
        for (int i = 0; i < 10; i++) {
            log.record(Diagnosis.problem(Cause.TPS_DEGRADED, Severity.WARNING, "e" + i, Map.of()), i);
        }
        assertEquals(3, log.size());

        List<IncidentLog.Entry> recent = log.recent(10);
        assertEquals(3, recent.size());
        assertEquals(9L, recent.get(0).timestampMillis(), "последнее событие должно быть первым");
    }

    @Test
    @DisplayName("recent() отдаёт от свежих к старым")
    void recentIsNewestFirst() {
        IncidentLog log = new IncidentLog(10);
        log.record(Diagnosis.problem(Cause.TPS_DEGRADED, Severity.WARNING, "old", Map.of()), 1L);
        log.record(Diagnosis.problem(Cause.TPS_DEGRADED, Severity.WARNING, "new", Map.of()), 2L);

        List<IncidentLog.Entry> recent = log.recent(5);
        assertEquals(2L, recent.get(0).timestampMillis());
    }

    @Test
    @DisplayName("Очистка журнала работает")
    void clearWorks() {
        IncidentLog log = new IncidentLog(5);
        log.record(Diagnosis.problem(Cause.TPS_DEGRADED, Severity.WARNING, "x", Map.of()), 1L);
        log.clear();
        assertEquals(0, log.size());
        assertTrue(log.recent(10).isEmpty());
    }

    @Test
    @DisplayName("Ёмкость меньше одного — ошибка")
    void rejectsZeroCapacity() {
        try {
            new IncidentLog(0);
            throw new AssertionError("ожидалось исключение");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("capacity"));
        }
    }
}
