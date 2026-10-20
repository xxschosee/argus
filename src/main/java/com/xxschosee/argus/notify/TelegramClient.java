package com.xxschosee.argus.notify;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TelegramClient {

    private static final String API_BASE = "https://api.telegram.org";

    private final HttpClient http;
    private final String token;
    private final Logger logger;
    private final Duration timeout;

    public TelegramClient(String token, Logger logger, Duration timeout) {
        this.token = token;
        this.logger = logger;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean sendMessage(long chatId, String text) {
        String body = "chat_id=" + chatId
                + "&text=" + encode(text)
                + "&parse_mode=HTML"
                + "&disable_web_page_preview=true";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/bot" + token + "/sendMessage"))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() == 200 && response.body().contains("\"ok\":true");
            if (!ok) {
                logger.warning("Telegram отклонил сообщение: HTTP " + response.statusCode()
                        + " " + truncate(response.body(), 200));
            }
            return ok;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Ошибка отправки в Telegram", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean verify(long chatId) {
        return sendMessage(chatId, "&#9881; <b>Подключён</b>. Буду сообщать о лагах.");
    }

    public static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
