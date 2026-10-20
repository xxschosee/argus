package dev.argus.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Превращает снимок метрик в диагнозы.
 *
 * Ключевое свойство — edge-trigger: тревога срабатывает один раз на эпизод деградации,
 * а не на каждом снимке. Как только причина ушла — приходит диагноз восстановления, и счётчик
 * сбрасывается. Без этого плагин завалил бы админа сообщениями.
 *
 * Класс намеренно не зависит от Bukkit: вся логика проверяется обычными юнит-тестами.
 */
public final class DiagnosisEngine {

    private final Thresholds thresholds;
    private final Map<Cause, Integer> streaks = new EnumMap<>(Cause.class);
    private final Map<Cause, Boolean> firing = new EnumMap<>(Cause.class);

    public DiagnosisEngine(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    /**
     * Разобрать очередной снимок.
     *
     * @return список событий: новые диагнозы и сообщения о восстановлении.
     *         Пустой список — значит ничего не изменилось.
     */
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

    /** Сбросить состояние — например, после ручной перезагрузки конфига. */
    public void reset() {
        streaks.clear();
        firing.clear();
    }

    /** Сейчас активна ли эта причина (для /argus status). */
    public boolean isFiring(Cause cause) {
        return firing.getOrDefault(cause, Boolean.FALSE);
    }

    /** Текущая длина серии плохих снимков по причине. */
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
            case HIGH_MSPT:
                if (s.mspt() > thresholds.criticalMspt()) {
                    return Severity.CRITICAL;
                }
                return s.mspt() > thresholds.warnMspt() ? Severity.WARNING : Severity.INFO;
            case HEAP_PRESSURE:
                if (s.heapRatio() > thresholds.criticalHeapRatio()) {
                    return Severity.CRITICAL;
                }
                return s.heapRatio() > thresholds.warnHeapRatio() ? Severity.WARNING : Severity.INFO;
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
            case HIGH_MSPT -> {
                ev.put("mspt", String.format(java.util.Locale.ROOT, "%.1f", s.mspt()));
                ev.put("tps_1m", String.format(java.util.Locale.ROOT, "%.2f", s.safeTps()));
                ev.put("warn_limit", String.valueOf(thresholds.warnMspt()));
            }
            case HEAP_PRESSURE -> {
                ev.put("heap_used_mb", String.valueOf(s.heapUsedBytes() / 1024 / 1024));
                ev.put("heap_max_mb", String.valueOf(s.heapMaxBytes() / 1024 / 1024));
                ev.put("heap_ratio", String.format(java.util.Locale.ROOT, "%.2f", s.heapRatio()));
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
                // no evidence for unknown cause
            }
        }
        return ev;
    }

    private String summaryOf(Cause cause, MetricsSnapshot s) {
        return switch (cause) {
            case TPS_DEGRADED -> String.format(java.util.Locale.ROOT,
                    "TPS dropped to %.2f (warn below %.1f)", s.safeTps(), thresholds.warnTps());
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
