package com.xxschosee.argus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealEngineTest {

    private static Diagnosis problem(Cause cause) {
        return Diagnosis.problem(cause, Severity.WARNING, "summary", Map.of("k", "v"));
    }

    @Test
    @DisplayName("План по умолчанию не содержит разрушительных действий")
    void defaultPolicyIsSafe() {
        HealEngine engine = HealEngine.defaultPolicy();
        for (Cause cause : Cause.values()) {
            for (HealAction action : engine.plan(problem(cause))) {
                assertFalse(action.isDestructive(),
                        "действие " + action.kind() + " для " + cause + " включено по умолчанию");
            }
        }
    }

    @Test
    @DisplayName("Каждой причине соответствует свой набор действий")
    void everyCauseHasItsOwnPlan() {
        HealEngine engine = HealEngine.defaultPolicy();
        List<HealAction> tps = engine.plan(problem(Cause.TPS_DEGRADED));
        List<HealAction> heap = engine.plan(problem(Cause.HEAP_PRESSURE));
        assertFalse(tps.isEmpty());
        assertFalse(heap.isEmpty());
        assertFalse(tps.equals(heap), "TPS и память должны лечиться по-разному");
    }

    @Test
    @DisplayName("Для восстановления план пустой")
    void recoveryHasNoPlan() {
        HealEngine engine = HealEngine.defaultPolicy();
        assertTrue(engine.plan(Diagnosis.recovered(Cause.TPS_DEGRADED, "ok")).isEmpty());
    }

    @Test
    @DisplayName("Чанковые действия получают цель, глобальные — нет")
    void targetAppliesOnlyToChunkActions() {
        Map<Cause, List<String>> policy = Map.of(
                Cause.ENTITY_OVERLOAD, List.of("unload_chunk", "trim_memory"));
        HealEngine engine = HealEngine.fromConfig(policy);

        List<HealAction> plan = engine.planWithTarget(
                problem(Cause.ENTITY_OVERLOAD), "chunk:world:12,-8");

        HealAction unload = plan.stream()
                .filter(a -> a.kind() == HealKind.UNLOAD_CHUNK)
                .findFirst()
                .orElseThrow();
        HealAction trim = plan.stream()
                .filter(a -> a.kind() == HealKind.TRIM_MEMORY)
                .findFirst()
                .orElseThrow();

        assertEquals("chunk:world:12,-8", unload.target());
        assertEquals("", trim.target(), "глобальное действие не должно получать цель чанка");
    }

    @Test
    @DisplayName("Без цели чанковое действие остаётся без адреса")
    void noTargetLeavesChunkActionUntargeted() {
        Map<Cause, List<String>> policy = Map.of(
                Cause.ENTITY_OVERLOAD, List.of("unload_chunk"));
        HealEngine engine = HealEngine.fromConfig(policy);
        List<HealAction> plan = engine.planWithTarget(problem(Cause.ENTITY_OVERLOAD), null);
        assertEquals("", plan.get(0).target());
    }

    @Test
    @DisplayName("Неизвестное действие в конфиге не ломает планирование")
    void unknownActionFallsBackToNotify() {
        Map<Cause, List<String>> policy = Map.of(
                Cause.TPS_DEGRADED, List.of("какая-то дичь", "unload_chunk"));
        HealEngine engine = HealEngine.fromConfig(policy);
        List<HealAction> plan = engine.plan(problem(Cause.TPS_DEGRADED));
        assertEquals(2, plan.size());
        assertEquals(HealKind.NOTIFY_ONLY, plan.get(0).kind());
        assertEquals(HealKind.UNLOAD_CHUNK, plan.get(1).kind());
    }

    @Test
    @DisplayName("Причине без записи в политике достаётся пустой план")
    void missingCauseYieldsEmptyPlan() {
        HealEngine engine = HealEngine.fromConfig(Map.of());
        assertTrue(engine.plan(problem(Cause.CHUNK_OVERLOAD)).isEmpty());
    }

    @Test
    @DisplayName("Ключи конфига разбираются в обоих регистрах и с дефисом")
    void actionKeysAreParsedLeniently() {
        assertEquals(HealKind.UNLOAD_CHUNK, HealKind.fromKey("UNLOAD_CHUNK"));
        assertEquals(HealKind.UNLOAD_CHUNK, HealKind.fromKey("unload-chunk"));
        assertEquals(HealKind.UNLOAD_CHUNK, HealKind.fromKey(" unload_chunk "));
        assertEquals(HealKind.NOTIFY_ONLY, HealKind.fromKey("что-то ещё"));
        assertEquals(HealKind.NOTIFY_ONLY, HealKind.fromKey(null));
    }

    @Test
    @DisplayName("План нельзя изменить извне")
    void planIsImmutable() {
        HealEngine engine = HealEngine.defaultPolicy();
        List<HealAction> plan = engine.plan(problem(Cause.TPS_DEGRADED));
        assertThrowsUnsupported(() -> plan.add(HealAction.global(HealKind.RESTART_SERVER, "x")));
    }

    private static void assertThrowsUnsupported(Runnable r) {
        try {
            r.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("ожидалась UnsupportedOperationException");
    }
}
