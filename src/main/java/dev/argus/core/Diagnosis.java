package dev.argus.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Постановка диагноза: что случилось, насколько это серьёзно и какими цифрами
 * это подтверждено.
 *
 * @param cause     причина
 * @param severity  насколько серьёзно
 * @param resolved  true — проблема ушла, а не возникла (уведомление о восстановлении)
 * @param summary   короткий текст для админа
 * @param evidence  цифры-доказательства, ключ → значение
 */
public record Diagnosis(
        Cause cause,
        Severity severity,
        boolean resolved,
        String summary,
        Map<String, String> evidence
) {

    public Diagnosis {
        if (cause == null) {
            throw new IllegalArgumentException("cause обязателен");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity обязателен");
        }
        evidence = evidence == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }

    public static Diagnosis problem(Cause cause, Severity severity, String summary, Map<String, String> evidence) {
        return new Diagnosis(cause, severity, false, summary, evidence);
    }

    public static Diagnosis recovered(Cause cause, String summary) {
        return new Diagnosis(cause, Severity.INFO, true, summary, Map.of());
    }

    /** Строки доказательств в стабильном порядке — для сообщений и логов. */
    public String evidenceLines() {
        StringBuilder sb = new StringBuilder();
        evidence.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));
        return sb.toString();
    }
}
