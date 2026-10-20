package com.xxschosee.argus.notify;

import com.xxschosee.argus.core.Cause;
import com.xxschosee.argus.core.Diagnosis;
import com.xxschosee.argus.core.HealAction;
import com.xxschosee.argus.core.HealKind;
import com.xxschosee.argus.core.Severity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRendererTest {

    private static Diagnosis problem(Cause cause) {
        return Diagnosis.problem(cause, Severity.WARNING, "TPS dropped to 12.40", Map.of("tps_1m", "12.40"));
    }

    @Test
    @DisplayName("Сообщение содержит причину, доказательства и имя сервера")
    void messageContainsEssentials() {
        MessageRenderer renderer = new MessageRenderer(MessageRenderer.Language.RU, "Paper 1.21.11");
        String text = renderer.render(problem(Cause.TPS_DEGRADED), List.of());

        assertTrue(text.contains("TPS упал"));
        assertTrue(text.contains("Paper 1.21.11"));
        assertTrue(text.contains("tps_1m"));
        assertTrue(text.contains("12.40"));
        assertTrue(text.contains("Автоматических действий не выполнялось"));
    }

    @Test
    @DisplayName("Список выполненных действий попадает в сообщение")
    void messageListsActions() {
        MessageRenderer renderer = new MessageRenderer(MessageRenderer.Language.RU, "srv");
        String text = renderer.render(problem(Cause.HIGH_MSPT), List.of(
                new HealAction(HealKind.TRIM_MEMORY, "", "Принудительная сборка мусора")));

        assertTrue(text.contains("Что сделано:"));
        assertTrue(text.contains("Принудительная сборка мусора"));
    }

    @Test
    @DisplayName("Восстановление читается как хорошая новость")
    void recoveryLooksGood() {
        MessageRenderer renderer = new MessageRenderer(MessageRenderer.Language.RU, "srv");
        String text = renderer.render(Diagnosis.recovered(Cause.TPS_DEGRADED, "ok"), List.of());

        assertTrue(text.contains("Проблема ушла"));
        assertFalse(text.contains("Автоматических действий"));
    }

    @Test
    @DisplayName("Английский вариант отличается от русского")
    void englishMessages() {
        MessageRenderer ru = new MessageRenderer(MessageRenderer.Language.RU, "srv");
        MessageRenderer en = new MessageRenderer(MessageRenderer.Language.EN, "srv");

        assertTrue(ru.render(problem(Cause.HEAP_PRESSURE), List.of()).contains("Закончилась память"));
        assertTrue(en.render(problem(Cause.HEAP_PRESSURE), List.of()).contains("Out of memory"));
    }

    @Test
    @DisplayName("Спецсимволы экранируются, чтобы не сломать HTML-разметку")
    void escapesHtml() {
        MessageRenderer renderer = new MessageRenderer(MessageRenderer.Language.RU, "<b>evil</b>");
        String text = renderer.render(problem(Cause.TPS_DEGRADED), List.of());

        assertFalse(text.contains("<b>evil</b>"));
        assertTrue(text.contains("&lt;b&gt;evil&lt;/b&gt;"));
    }

    @Test
    @DisplayName("Экранирование покрывает все три опасных символа")
    void escapeHtmlCoversAll() {
        assertTrue(TelegramClient.escapeHtml("a & b").contains("&amp;"));
        assertTrue(TelegramClient.escapeHtml("a < b").contains("&lt;"));
        assertTrue(TelegramClient.escapeHtml("a > b").contains("&gt;"));
        assertTrue(TelegramClient.escapeHtml(null).isEmpty());
    }

    @Test
    @DisplayName("Критическая проблема помечается отдельной иконкой")
    void criticalHasItsOwnIcon() {
        MessageRenderer renderer = new MessageRenderer(MessageRenderer.Language.RU, "srv");
        String critical = renderer.render(
                Diagnosis.problem(Cause.TPS_DEGRADED, Severity.CRITICAL, "s", Map.of()), List.of());
        String warning = renderer.render(
                Diagnosis.problem(Cause.TPS_DEGRADED, Severity.WARNING, "s", Map.of()), List.of());

        assertFalse(critical.equals(warning));
    }
}
