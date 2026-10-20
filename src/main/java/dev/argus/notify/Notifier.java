package dev.argus.notify;

import dev.argus.core.Diagnosis;
import dev.argus.core.HealAction;
import java.util.List;

/** Приёмник сообщений админу. Реализации: Telegram, лог, заглушка. */
public interface Notifier {

    /**
     * Сообщить о диагнозе.
     *
     * @param diagnosis  что обнаружено
     * @param performed  какие действия реально выполнены
     */
    void notify(Diagnosis diagnosis, List<HealAction> performed);

    /** Служебное сообщение, не связанное с инцидентом. */
    void info(String message);

    /** Имя реализации для лога. */
    String name();

    /** Заглушка: пишет всё в лог, ничего никуда не отправляет. */
    static Notifier logging(java.util.logging.Logger logger) {
        return new Notifier() {
            @Override
            public void notify(Diagnosis diagnosis, List<HealAction> performed) {
                logger.warning("[Argus] " + diagnosis.cause().key() + " / " + diagnosis.severity()
                        + " — " + diagnosis.summary() + (performed.isEmpty() ? "" : " / actions=" + performed.size()));
            }

            @Override
            public void info(String message) {
                logger.info("[Argus] " + message);
            }

            @Override
            public String name() {
                return "log";
            }
        };
    }
}
