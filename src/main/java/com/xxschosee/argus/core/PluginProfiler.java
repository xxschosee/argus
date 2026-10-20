package com.xxschosee.argus.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кто занимает время сервера.
 *
 * Считает образцы времени, потраченного на код каждого владельца: плагин,
 * сам Paper или никто. Окно скользящее, чтобы отчёт отражал текущее состояние,
 * а не всё, что было с момента старта.
 */
public final class PluginProfiler {

    /** Кто занимает время, когда в стеке нет ни одного плагина. */
    public static final String SERVER = "Paper";

    private final Deque<Sample> window = new ArrayDeque<>();
    private final Map<String, Integer> totals = new LinkedHashMap<>();
    private final long windowMillis;

    public PluginProfiler(long windowSeconds) {
        this.windowMillis = Math.max(1000L, windowSeconds * 1000L);
    }

    public void record(String owner, long now) {
        window.addLast(new Sample(now, owner));
        totals.merge(owner, 1, Integer::sum);
        prune(now);
    }

    private void prune(long now) {
        while (!window.isEmpty() && now - window.getFirst().time() > windowMillis) {
            window.removeFirst();
        }
    }

    /** Распределение времени по окну: владелец -> доля в процентах. */
    public Map<String, Double> distribution() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0;
        for (Sample sample : window) {
            counts.merge(sample.owner(), 1, Integer::sum);
            total++;
        }
        Map<String, Double> result = new LinkedHashMap<>();
        if (total == 0) {
            return result;
        }
        int sampleCount = total;
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue() * 100.0 / sampleCount));
        return result;
    }

    /** Кто занимает время больше всех. */
    public String top() {
        List<String> owners = new ArrayList<>(distribution().keySet());
        return owners.isEmpty() ? null : owners.get(0);
    }

    public int sampleCount() {
        return window.size();
    }

    public void reset() {
        window.clear();
        totals.clear();
    }

    public Map<String, Integer> totals() {
        return Map.copyOf(totals);
    }

    private record Sample(long time, String owner) {
    }
}
