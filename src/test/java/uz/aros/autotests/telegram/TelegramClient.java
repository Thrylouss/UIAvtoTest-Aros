package uz.aros.autotests.telegram;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * Минимальный клиент Telegram Bot API на стандартном {@code java.net.http}: отправка текста,
 * фото и файлов, получение команд (long polling). Никаких внешних SDK.
 *
 * <p>Документация API: https://core.telegram.org/bots/api
 */
public final class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger("QA");
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public TelegramClient(String token) {
        this.token = token;
    }

    private String url(String method) {
        return "https://api.telegram.org/bot" + token + "/" + method;
    }

    /** Отправить текст в HTML-разметке. Возвращает true, если Telegram принял сообщение. */
    public boolean sendMessage(String chatId, String html) {
        JsonObject body = new JsonObject();
        body.addProperty("chat_id", chatId);
        body.addProperty("text", html);
        body.addProperty("parse_mode", "HTML");
        body.addProperty("disable_web_page_preview", true);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url("sendMessage")))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
            return check(http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)), "sendMessage");
        } catch (Exception e) {
            log.warn("Telegram sendMessage: {}", e.toString());
            return false;
        }
    }

    public boolean sendPhoto(String chatId, Path photo, String captionHtml) {
        return sendFile("sendPhoto", "photo", chatId, photo, captionHtml);
    }

    public boolean sendDocument(String chatId, Path file, String captionHtml) {
        return sendFile("sendDocument", "document", chatId, file, captionHtml);
    }

    private boolean sendFile(String method, String field, String chatId, Path file, String caption) {
        try {
            String boundary = "----qa" + UUID.randomUUID();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            part(out, boundary, "chat_id", chatId);
            if (caption != null && !caption.isBlank()) {
                part(out, boundary, "caption", caption.length() > 1000 ? caption.substring(0, 1000) + "…" : caption);
                part(out, boundary, "parse_mode", "HTML");
            }
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field + "\"; filename=\""
                    + file.getFileName() + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(Files.readAllBytes(file));
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url(method)))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
            return check(http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)), method);
        } catch (Exception e) {
            log.warn("Telegram {}: {}", method, e.toString());
            return false;
        }
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    /** Long polling: ждёт до 30 секунд новых сообщений. offset = id последнего обработанного + 1. */
    public JsonArray getUpdates(long offset) throws IOException, InterruptedException {
        String u = url("getUpdates") + "?timeout=30&offset=" + offset + "&allowed_updates="
                + URLEncoder.encode("[\"message\"]", StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(45)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
            throw new IOException("getUpdates: " + res.body());
        }
        return json.getAsJsonArray("result");
    }

    private boolean check(HttpResponse<String> res, String what) {
        if (res.statusCode() == 200) return true;
        log.warn("Telegram {} → HTTP {}: {}", what, res.statusCode(), res.body());
        return false;
    }
}
