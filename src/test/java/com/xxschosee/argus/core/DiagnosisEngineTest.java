package com.xxschosee.argus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiagnosisEngineTest {

    private static final Thresholds T = new Thresholds(
            45.0, 70.0,
            17.0, 12.0,
            0.80, 0.92,
            3000, 4000,
            3);

    private static MetricsSnapshot healthy() {
        return new MetricsSnapshot(1L, 12.0, 20.0, 20.0, 20.0,
                512L * 1024 * 1024, 2048L * 1024 * 1024, 10, 100, 200, 500);
    }

    private static MetricsSnapshot withMspt(double mspt) {
        MetricsSnapshot h = healthy();
        return new MetricsSnapshot(2L, mspt, 19.0, 19.0, 19.0,
                h.heapUsedBytes(), h.heapMaxBytes(), h.playersOnline(), h.playersMax(),
                h.entitiesLoaded(), h.chunksLoaded());
    }

    private static MetricsSnapshot withTps(double tps) {
        MetricsSnapshot h = healthy();
        return new MetricsSnapshot(3L, 15.0, tps, tps, tps,
                h.heapUsedBytes(), h.heapMaxBytes(), h.playersOnline(), h.playersMax(),
                h.entitiesLoaded(), h.chunksLoaded());
    }

    private static MetricsSnapshot withHeap(double usedRatio) {
        long max = 2048L * 1024 * 1024;
        MetricsSnapshot h = healthy();
        return new MetricsSnapshot(4L, 12.0, 20.0, 20.0, 20.0,
                (long) (max * usedRatio), max, h.playersOnline(), h.playersMax(),
                h.entitiesLoaded(), h.chunksLoaded());
    }

    private static MetricsSnapshot withEntities(int entities) {
        MetricsSnapshot h = healthy();
        return new MetricsSnapshot(5L, 12.0, 20.0, 20.0, 20.0,
                h.heapUsedBytes(), h.heapMaxBytes(), h.playersOnline(), h.playersMax(),
                entities, h.chunksLoaded());
    }

    private static MetricsSnapshot withChunks(int chunks) {
        MetricsSnapshot h = healthy();
        return new MetricsSnapshot(6L, 12.0, 20.0, 20.0, 20.0,
                h.heapUsedBytes(), h.heapMaxBytes(), h.playersOnline(), h.playersMax(),
                h.entitiesLoaded(), chunks);
    }

    /** Прокормить серию одинаковых снимков и вернуть события, возникшие на последнем. */
    private static List<Diagnosis> feed(DiagnosisEngine engine, MetricsSnapshot s, int times) {
        List<Diagnosis> events = List.of();
        for (int i = 0; i < times; i++) {
            events = engine.analyze(s);
        }
        return events;
    }

    @Test
    @DisplayName("На здоровом сервере событий нет")
    void healthyServerIsSilent() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        assertTrue(engine.analyze(healthy()).isEmpty());
    }

    @Test
    @DisplayName("Тревога срабатывает только на третий плохой замер подряд")
    void alarmWaitsForConsecutiveSamples() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        MetricsSnapshot bad = withTps(10.0);

        assertTrue(engine.analyze(bad).isEmpty(), "первый плохой замер не должен поднимать тревогу");
        assertTrue(engine.analyze(bad).isEmpty(), "второй плохой замер не должен поднимать тревогу");

        List<Diagnosis> events = engine.analyze(bad);
        assertEquals(1, events.size(), "третий плохой замер должен поднять тревогу");
        assertEquals(Cause.TPS_DEGRADED, events.get(0).cause());
        assertEquals(Severity.CRITICAL, events.get(0).severity());
        assertFalse(events.get(0).resolved());
    }

    @Test
    @DisplayName("Пока проблема не исчезла, повторных тревог нет")
    void noRepeatAlarmsWhileProblemPersists() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        MetricsSnapshot bad = withTps(10.0);
        feed(engine, bad, 3);

        for (int i = 0; i < 10; i++) {
            assertTrue(engine.analyze(bad).isEmpty(), "повторная тревога в том же эпизоде недопустима");
        }
    }

    @Test
    @DisplayName("После восстановления приходит событие и счётчик сбрасывается")
    void recoveryIsReportedAndResetsCounter() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        MetricsSnapshot bad = withTps(10.0);
        feed(engine, bad, 3);
        assertTrue(engine.isFiring(Cause.TPS_DEGRADED));

        List<Diagnosis> events = engine.analyze(healthy());
        assertEquals(1, events.size());
        assertTrue(events.get(0).resolved());
        assertEquals(Cause.TPS_DEGRADED, events.get(0).cause());
        assertFalse(engine.isFiring(Cause.TPS_DEGRADED));
        assertEquals(0, engine.streak(Cause.TPS_DEGRADED));

        // после сброса тревога снова ждёт три замера
        assertTrue(engine.analyze(bad).isEmpty());
        assertTrue(engine.analyze(bad).isEmpty());
        assertEquals(1, engine.analyze(bad).size());
    }

    @Test
    @DisplayName("Одиночный всплеск не вызывает тревогу")
    void singleSpikeIsIgnored() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        assertTrue(engine.analyze(withTps(5.0)).isEmpty());
        assertTrue(engine.analyze(healthy()).isEmpty());
        assertTrue(engine.analyze(withTps(5.0)).isEmpty());
        assertTrue(engine.analyze(healthy()).isEmpty());
    }

    @Test
    @DisplayName("Уровень WARNING между порогами")
    void warnsBetweenThresholds() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        List<Diagnosis> events = feed(engine, withMspt(50.0), 3);
        assertEquals(1, events.size());
        assertEquals(Cause.HIGH_MSPT, events.get(0).cause());
        assertEquals(Severity.WARNING, events.get(0).severity());
    }

    @Test
    @DisplayName("Уровень CRITICAL за критическим порогом")
    void criticalBeyondThreshold() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        List<Diagnosis> events = feed(engine, withMspt(90.0), 3);
        assertEquals(1, events.size());
        assertEquals(Severity.CRITICAL, events.get(0).severity());
    }

    @Test
    @DisplayName("Перегрузка кучей распознаётся")
    void detectsHeapPressure() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        List<Diagnosis> events = feed(engine, withHeap(0.95), 3);
        assertEquals(1, events.size());
        assertEquals(Cause.HEAP_PRESSURE, events.get(0).cause());
        assertEquals(Severity.CRITICAL, events.get(0).severity());
    }

    @Test
    @DisplayName("Перегрузка сущностями распознаётся")
    void detectsEntityOverload() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        List<Diagnosis> events = feed(engine, withEntities(5000), 3);
        assertEquals(1, events.size());
        assertEquals(Cause.ENTITY_OVERLOAD, events.get(0).cause());
        assertEquals(Severity.WARNING, events.get(0).severity());
    }

    @Test
    @DisplayName("Критическая перегрузка сущностями распознаётся")
    void detectsCriticalEntityOverload() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        List<Diagnosis> events = feed(engine, withEntities(7000), 3);
        assertEquals(1, events.size());
        assertEquals(Severity.CRITICAL, events.get(0).severity());
    }

    @Test
    @DisplayName("Перегрузка чанками распознаётся")
    void detectsChunkOverload() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        List<Diagnosis> events = feed(engine, withChunks(5000), 3);
        assertEquals(1, events.size());
        assertEquals(Cause.CHUNK_OVERLOAD, events.get(0).cause());
    }

    @Test
    @DisplayName("Доказательства диагноза содержат ключевые цифры")
    void diagnosisCarriesEvidence() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        List<Diagnosis> events = feed(engine, withTps(11.5), 3);
        Diagnosis d = events.get(0);
        assertTrue(d.evidence().containsKey("tps_1m"));
        assertTrue(d.evidence().get("tps_1m").startsWith("11.5"));
        assertTrue(d.evidenceLines().contains("tps_1m"));
    }

    @Test
    @DisplayName("Сброс движка очищает состояние тревог")
    void resetClearsState() {
        DiagnosisEngine engine = new DiagnosisEngine(T);
        feed(engine, withTps(10.0), 3);
        assertTrue(engine.isFiring(Cause.TPS_DEGRADED));
        engine.reset();
        assertFalse(engine.isFiring(Cause.TPS_DEGRADED));
        assertEquals(0, engine.streak(Cause.TPS_DEGRADED));
    }
}
