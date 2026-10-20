package com.xxschosee.argus.notify;

import com.xxschosee.argus.core.Diagnosis;
import com.xxschosee.argus.core.HealAction;
import java.util.List;

public interface Notifier {

    void notify(Diagnosis diagnosis, List<HealAction> performed);

    void info(String message);

    String name();

    static Notifier logging(java.util.logging.Logger logger) {
        return new Notifier() {
            @Override
            public void notify(Diagnosis diagnosis, List<HealAction> performed) {
                logger.warning("" + diagnosis.cause().key() + " / " + diagnosis.severity()
                        + " — " + diagnosis.summary() + (performed.isEmpty() ? "" : " / actions=" + performed.size()));
            }

            @Override
            public void info(String message) {
                logger.info("" + message);
            }

            @Override
            public String name() {
                return "log";
            }
        };
    }
}
