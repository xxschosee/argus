package com.xxschosee.argus.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HealEngine {

    private final Map<Cause, List<HealAction>> policy;

    public HealEngine(Map<Cause, List<HealAction>> policy) {
        Map<Cause, List<HealAction>> copy = new EnumMap<>(Cause.class);
        policy.forEach((cause, actions) -> copy.put(cause, List.copyOf(actions)));
        this.policy = Collections.unmodifiableMap(copy);
    }

    public List<HealAction> plan(Diagnosis diagnosis) {
        if (diagnosis.resolved()) {
            return List.of();
        }
        return policy.getOrDefault(diagnosis.cause(), List.of());
    }

    public HealEngine withPolicy(Cause cause, List<HealAction> actions) {
        Map<Cause, List<HealAction>> next = new EnumMap<>(policy);
        next.put(cause, List.copyOf(actions));
        return new HealEngine(next);
    }

    public static HealEngine defaultPolicy() {
        Map<Cause, List<HealAction>> policy = new EnumMap<>(Cause.class);

        policy.put(Cause.TPS_DEGRADED, List.of(
                HealAction.global(HealKind.NOTIFY_ONLY, "Сообщить администратору о падении TPS"),
                HealAction.global(HealKind.TRIM_MEMORY, "Принудительная сборка мусора")
        ));
        policy.put(Cause.HIGH_MSPT, List.of(
                HealAction.global(HealKind.NOTIFY_ONLY, "Сообщить администратору о времени тика"),
                HealAction.global(HealKind.TRIM_MEMORY, "Принудительная сборка мусора")
        ));
        policy.put(Cause.HEAP_PRESSURE, List.of(
                HealAction.global(HealKind.TRIM_MEMORY, "Принудительная сборка мусора"),
                HealAction.global(HealKind.NOTIFY_ONLY, "Сообщить администратору о памяти")
        ));
        policy.put(Cause.ENTITY_OVERLOAD, List.of(
                HealAction.global(HealKind.NOTIFY_ONLY, "Сообщить администратору о сущностях")
        ));
        policy.put(Cause.CHUNK_OVERLOAD, List.of(
                HealAction.global(HealKind.NOTIFY_ONLY, "Сообщить администратору о чанках")
        ));

        return new HealEngine(policy);
    }

    public static HealEngine fromConfig(Map<Cause, List<String>> config) {
        Map<Cause, List<HealAction>> policy = new EnumMap<>(Cause.class);
        for (Map.Entry<Cause, List<String>> entry : config.entrySet()) {
            List<HealAction> actions = new ArrayList<>();
            for (String raw : entry.getValue()) {
                HealKind kind = HealKind.fromKey(raw);
                actions.add(new HealAction(kind, "", kind.key()));
            }
            policy.put(entry.getKey(), actions);
        }
        return new HealEngine(policy);
    }

    public List<HealAction> planWithTarget(Diagnosis diagnosis, String target) {
        List<HealAction> base = plan(diagnosis);
        if (base.isEmpty() || target == null || target.isEmpty()) {
            return base;
        }
        List<HealAction> targeted = new ArrayList<>(base.size());
        for (HealAction action : base) {
            boolean chunkScoped = action.kind() == HealKind.UNLOAD_CHUNK
                    || action.kind() == HealKind.REMOVE_ENTITIES;
            targeted.add(chunkScoped
                    ? new HealAction(action.kind(), target, action.description())
                    : action);
        }
        return targeted;
    }

    public Map<String, List<String>> describe() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        policy.forEach((cause, actions) -> out.put(cause.key(),
                actions.stream().map(a -> a.kind().key()).toList()));
        return out;
    }
}
