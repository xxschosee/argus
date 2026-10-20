package com.xxschosee.argus.notify;

import com.xxschosee.argus.core.Diagnosis;
import com.xxschosee.argus.core.HealAction;
import java.util.List;

/**
 * Отправляет сообщения админу в Telegram.
 * Форматирование вынесено в {@link MessageRenderer}, здесь — только доставка.
 */
public final class TelegramNotifier implements Notifier {

    private final TelegramClient client;
    private final long chatId;
    private final MessageRenderer renderer;

    public TelegramNotifier(TelegramClient client, long chatId, MessageRenderer renderer) {
        this.client = client;
        this.chatId = chatId;
        this.renderer = renderer;
    }

    @Override
    public void notify(Diagnosis diagnosis, List<HealAction> performed) {
        client.sendMessage(chatId, renderer.render(diagnosis, performed));
    }

    @Override
    public void info(String message) {
        client.sendMessage(chatId, renderer.renderInfo(message));
    }

    @Override
    public String name() {
        return "telegram";
    }
}
