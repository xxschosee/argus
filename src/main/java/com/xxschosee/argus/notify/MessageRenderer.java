package com.xxschosee.argus.notify;

import com.xxschosee.argus.core.Cause;
import com.xxschosee.argus.core.Diagnosis;
import com.xxschosee.argus.core.HealAction;
import com.xxschosee.argus.core.Severity;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MessageRenderer {

    public enum Language {
        RU,
        EN;

        public static Language fromKey(String key) {
            if (key == null) {
                return RU;
            }
            return switch (key.trim().toLowerCase(Locale.ROOT)) {
                case "en", "english" -> EN;
                default -> RU;
            };
        }
    }

    private static final Map<Cause, String> TITLE_RU = Map.of(
            Cause.TPS_DEGRADED, "TPS упал",
            Cause.HIGH_MSPT, "Сервер не успевает за тиками",
            Cause.HEAP_PRESSURE, "Закончилась память",
            Cause.ENTITY_OVERLOAD, "Слишком много сущностей",
            Cause.CHUNK_OVERLOAD, "Слишком много чанков"
    );

    private static final Map<Cause, String> TITLE_EN = Map.of(
            Cause.TPS_DEGRADED, "TPS dropped",
            Cause.HIGH_MSPT, "Server is missing tick time",
            Cause.HEAP_PRESSURE, "Out of memory",
            Cause.ENTITY_OVERLOAD, "Too many entities",
            Cause.CHUNK_OVERLOAD, "Too many chunks"
    );

    private final Language language;
    private final String serverName;

    public MessageRenderer(Language language, String serverName) {
        this.language = language;
        this.serverName = serverName == null ? "" : serverName;
    }

    public String render(Diagnosis diagnosis, List<HealAction> performed) {
        StringBuilder sb = new StringBuilder();
        String icon = diagnosis.severity() == Severity.CRITICAL ? "&#128308;" : "&#9881;";

        sb.append(icon).append(' ').append("<b>").append(TelegramClient.escapeHtml(title(diagnosis.cause()))).append("</b>");

        if (diagnosis.resolved()) {
            sb.append("\n").append(escape(language == Language.RU
                    ? "Проблема ушла, сервер в норме."
                    : "Resolved, the server is back to normal."));
            sb.append("\n").append(escape(serverName));
            return sb.toString();
        }

        sb.append("\n").append(escape(serverName));
        sb.append("\n").append(escape(diagnosis.summary()));

        if (!diagnosis.evidence().isEmpty()) {
            sb.append("\n\n<code>");
            diagnosis.evidence().forEach((k, v) ->
                    sb.append(escape(k)).append(": ").append(escape(v)).append('\n'));
            sb.append("</code>");
        }

        if (performed != null && !performed.isEmpty()) {
            sb.append("\n").append(escape(language == Language.RU ? "Что сделано:" : "Actions taken:"));
            for (HealAction action : performed) {
                sb.append("\n• ").append(escape(action.description()));
            }
        } else {
            sb.append("\n").append(escape(language == Language.RU
                    ? "Автоматических действий не выполнялось."
                    : "No automatic actions were taken."));
        }

        return sb.toString();
    }

    public String renderInfo(String message) {
        return "&#9881; " + escape(message);
    }

    public String title(Cause cause) {
        return language == Language.RU ? TITLE_RU.get(cause) : TITLE_EN.get(cause);
    }

    public Language language() {
        return language;
    }

    private static String escape(String raw) {
        return TelegramClient.escapeHtml(raw);
    }
}
