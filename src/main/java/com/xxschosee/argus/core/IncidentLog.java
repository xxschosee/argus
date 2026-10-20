package com.xxschosee.argus.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class IncidentLog {

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int capacity;

    public IncidentLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity должен быть >= 1");
        }
        this.capacity = capacity;
    }

    public synchronized void record(Diagnosis diagnosis, long timestampMillis) {
        entries.addLast(new Entry(timestampMillis, diagnosis.cause(), diagnosis.severity(),
                diagnosis.resolved(), diagnosis.summary()));
        while (entries.size() > capacity) {
            entries.removeFirst();
        }
    }

    public synchronized List<Entry> recent(int limit) {
        List<Entry> all = new ArrayList<>(entries);
        java.util.Collections.reverse(all);
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    public record Entry(
            long timestampMillis,
            Cause cause,
            Severity severity,
            boolean resolved,
            String summary
    ) {
    }
}
