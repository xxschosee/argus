package dev.argus.platform;

import dev.argus.core.HealAction;

/**
 * Применяет лечебное действие к живому серверу.
 * Ядро только планирует, здесь — исполнение.
 */
public interface HealExecutor {

    /**
     * Применить действие.
     *
     * @return true, если действие выполнено; false — если не поддерживается
     *         или не получилось.
     */
    boolean apply(HealAction action);

    /** Короткое имя реализации для лога. */
    String name();
}
