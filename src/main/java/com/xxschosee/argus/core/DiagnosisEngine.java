package com.xxschosee.argus.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiagnosisEngine {

    private final Thresholds thresholds;
    private final Map<Cause, Integer> streaks = new EnumMap<>(Cause.class);
    private final Map<Cause, Boolean> firing = new EnumMap<>(Cause.class);

    public DiagnosisEngine(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public List<Diagnosis> analyze(MetricsSnapshot snapshot) {
        List<Diagnosis> events = new ArrayList<>();
        for (Cause cause : Cause.values()) {
            Severity severity = severityOf(cause, snapshot);
            boolean bad = severity != Severity.INFO;
            boolean wasFiring = firing.getOrDefault(cause, Boolean.FALSE);

            int streak = bad ? streaks.merge(cause, 1, Integer::sum) : 0;
            streaks.put(cause, streak);

            if (bad && !wasFiring && streak >= thresholds.samplesBeforeAlarm()) {
                firing.put(cause, Boolean.TRUE);
                events.add(Diagnosis.problem(cause, severity, summaryOf(cause, snapshot), evidenceOf(cause, snapshot)));
            } else if (!bad && wasFiring) {
                firing.put(cause, Boolean.FALSE);
                events.add(Diagnosis.recovered(cause, summaryOf(cause, snapshot)));
            }
        }
        return events;
    }

    public void reset() {
        streaks.clear();
        firing.clear();
    }

    public boolean isFiring(Cause cause) {
        return firing.getOrDefault(cause, Boolean.FALSE);
    }

    public int streak(Cause cause) {
        return streaks.getOrDefault(cause, 0);
    }

    // ------------------------------------------------------------------

    private Severity severityOf(Cause cause, MetricsSnapshot s) {
        switch (cause) {
            case TPS_DEGRADED:
                if (s.safeTps() < thresholds.criticalTps()) {
                    return Severity.CRITICAL;
                }
                return s.safeTps() < thresholds.warnTps() ? Severity.WARNING : Severity.INFO;
            case TICK_SPIKE:
                if (s.ticksOverBudget() >= thresholds.spikesBeforeAlarm() * 2) {
                    return Severity.CRITICAL;
                }
                return s.ticksOverBudget() >= thresholds.spikesBeforeAlarm() ? Severity.WARNING : Severity.INFO;
            case HIGH_MSPT:
                if (s.mspt() > thresholds.criticalMspt()) {
                    return Severity.CRITICAL;
                }
                return s.mspt() > thresholds.warnMspt() ? Severity.WARNING : Severity.INFO;
            case HEAP_PRESSURE:
                if (s.heapRatio() > thresholds.criticalHeapRatio()) {
                    return Severity.CRITICAL;
                }
                boolean lagging = s.mspt() > thresholds.warnMspt() || s.safeTps() < thresholds.warnTps();
                if (s.heapRatio() > thresholds.warnHeapRatio() && lagging) {
                    return thresholds.heapNeedsLag() ? Severity.WARNING : Severity.INFO;
                }
                return Severity.INFO;
            case ENTITY_OVERLOAD:
                if (s.entitiesLoaded() > thresholds.maxEntities() * 2) {
                    return Severity.CRITICAL;
                }
                return s.entitiesLoaded() > thresholds.maxEntities() ? Severity.WARNING : Severity.INFO;
            case CHUNK_OVERLOAD:
                if (s.chunksLoaded() > thresholds.maxChunks() * 3 / 2) {
                    return Severity.CRITICAL;
                }
                return s.chunksLoaded() > thresholds.maxChunks() ? Severity.WARNING : Severity.INFO;
            default:
                return Severity.INFO;
        }
    }

    private Map<String, String> evidenceOf(Cause cause, MetricsSnapshot s) {
        Map<String, String> ev = new LinkedHashMap<>();
        switch (cause) {
            case TPS_DEGRADED -> {
                ev.put("tps_1m", String.format(java.util.Locale.ROOT, "%.2f", s.safeTps()));
                ev.put("tps_5m", String.format(java.util.Locale.ROOT, "%.2f", s.tps5m()));
                ev.put("mspt", String.format(java.util.Locale.ROOT, "%.1f", s.mspt()));
                ev.put("players", s.playersOnline() + "/" + s.playersMax());
            }
            case TICK_SPIKE -> {
                ev.put("spikes", s.ticksOverBudget() + " x >" + (long) thresholds.tickBudgetMs() + "ms");
                ev.put("worst_tick_ms", String.format(java.util.Locale.ROOT, "%.1f", s.worstTickMs()));
                ev.put("p99_tick_ms", String.format(java.util.Locale.ROOT, "%.1f", s.p99TickMs()));
                ev.put("budget_ms", String.valueOf((long) thresholds.tickBudgetMs()));
            }
            case HIGH_MSPT -> {
                ev.put("mspt", String.format(java.util.Locale.ROOT, "%.1f", s.mspt()));
                ev.put("tps_1m", String.format(java.util.Locale.ROOT, "%.2f", s.safeTps()));
                ev.put("warn_limit", String.valueOf(thresholds.warnMspt()));
            }
            case HEAP_PRESSURE -> {
                ev.put("heap_used_mb", String.valueOf(s.heapUsedBytes() / 1024 / 1024));
                ev.put("heap_max_mb", String.valueOf(s.heapMaxBytes() / 1024 / 1024));
                ev.put("heap_ratio", String.format(java.util.Locale.ROOT, "%.2f", s.heapRatio()));
                ev.put("mspt", String.format(java.util.Locale.ROOT, "%.1f", s.mspt()));
            }
            case ENTITY_OVERLOAD -> {
                ev.put("entities", String.valueOf(s.entitiesLoaded()));
                ev.put("limit", String.valueOf(thresholds.maxEntities()));
            }
            case CHUNK_OVERLOAD -> {
                ev.put("chunks", String.valueOf(s.chunksLoaded()));
                ev.put("limit", String.valueOf(thresholds.maxChunks()));
            }
            default -> {
            }
        }
        return ev;
    }

    private String summaryOf(Cause cause, MetricsSnapshot s) {
        return switch (cause) {
            case TPS_DEGRADED -> String.format(java.util.Locale.ROOT,
                    "TPS dropped to %.2f (warn below %.1f)", s.safeTps(), thresholds.warnTps());
            case TICK_SPIKE -> String.format(java.util.Locale.ROOT,
                    "%d ticks longer than %.0f ms, worst %.1f ms",
                    s.ticksOverBudget(), thresholds.tickBudgetMs(), s.worstTickMs());
            case HIGH_MSPT -> String.format(java.util.Locale.ROOT,
                    "Tick time %.1f ms (warn above %.0f ms)", s.mspt(), thresholds.warnMspt());
            case HEAP_PRESSURE -> String.format(java.util.Locale.ROOT,
                    "Heap usage %.0f%%", s.heapRatio() * 100);
            case ENTITY_OVERLOAD -> String.format(java.util.Locale.ROOT,
                    "%d entities loaded (limit %d)", s.entitiesLoaded(), thresholds.maxEntities());
            case CHUNK_OVERLOAD -> String.format(java.util.Locale.ROOT,
                    "%d chunks loaded (limit %d)", s.chunksLoaded(), thresholds.maxChunks());
        };
    }
}
