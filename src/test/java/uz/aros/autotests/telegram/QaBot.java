package uz.aros.autotests.telegram;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.aros.autotests.config.EnvConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Telegram-бот для запуска автотестов командами из чата.
 *
 * <p>Запуск (в отдельном терминале, пока он открыт — бот работает):
 * <pre>mvn test-compile exec:java@telegram-bot</pre>
 *
 * <p>Как это устроено: бот в цикле спрашивает у Telegram новые сообщения (long polling). Получив команду,
 * например {@code /smoke}, он запускает в этой же папке {@code mvn test -Dgroups=smoke} как обычный процесс.
 * Сам отчёт после прогона отправляет не бот, а слушатель {@code QaRunListener} внутри тестов — бот лишь
 * сообщает о старте/конце и присылает хвост лога, если сборка упала до тестов (например, ошибка компиляции).
 *
 * <p>Безопасность: команды принимаются только из чатов, перечисленных в {@code TELEGRAM_CHAT_ID}.
 */
public final class QaBot {

    private static final Logger log = LoggerFactory.getLogger("QA");
    private static final Path REPORT_HTML = Path.of("target", "qa-report", "report.html");
    private static final Path LAST_TXT = Path.of("target", "qa-report", "last-run.txt");

    private final TelegramClient tg;
    private final List<String> allowedChats;
    private final AtomicReference<Process> running = new AtomicReference<>();
    private final Deque<String> tail = new ArrayDeque<>();
    private volatile String currentLabel = "";
    private volatile long runStartedAt;

    private QaBot(TelegramClient tg, List<String> allowedChats) {
        this.tg = tg;
        this.allowedChats = allowedChats;
    }

    public static void main(String[] args) throws Exception {
        String token = EnvConfig.telegramToken();
        List<String> chats = EnvConfig.telegramChatIds();
        if (token.isBlank() || chats.isEmpty()) {
            System.err.println("""
                    Бот не настроен. Добавьте в файл .env две строки:
                      TELEGRAM_BOT_TOKEN=123456:ABC...   (токен от @BotFather)
                      TELEGRAM_CHAT_ID=123456789         (ваш chat id; узнать: напишите боту /start и откройте
                                                          https://api.telegram.org/bot<токен>/getUpdates)
                    Подробно — в README, раздел «Telegram-бот».""");
            System.exit(1);
        }
        new QaBot(new TelegramClient(token), chats).loop();
    }

    private void loop() throws InterruptedException {
        log.info("🤖 Бот запущен. Разрешённые чаты: {}. Остановить — Ctrl+C.", allowedChats);
        for (String chat : allowedChats) {
            tg.sendMessage(chat, "🤖 <b>Aros QA-бот запущен.</b>\nНапишите /help, чтобы увидеть команды.");
        }
        long offset = 0;
        while (true) {
            try {
                JsonArray updates = tg.getUpdates(offset);
                for (JsonElement el : updates) {
                    JsonObject u = el.getAsJsonObject();
                    offset = u.get("update_id").getAsLong() + 1;
                    if (u.has("message")) handle(u.getAsJsonObject("message"));
                }
            } catch (IOException e) {
                log.warn("Ошибка связи с Telegram: {} — повтор через 5 с", e.getMessage());
                Thread.sleep(5000);
            }
        }
    }

    private void handle(JsonObject msg) {
        if (!msg.has("text")) return;
        String chat = msg.getAsJsonObject("chat").get("id").getAsString();
        String text = msg.get("text").getAsString().trim();
        String who = msg.has("from") && msg.getAsJsonObject("from").has("username")
                ? "@" + msg.getAsJsonObject("from").get("username").getAsString()
                : (msg.has("from") ? msg.getAsJsonObject("from").get("first_name").getAsString() : "?");

        if (!allowedChats.contains(chat)) {
            log.warn("Игнорирую сообщение из чужого чата {} ({}): {}", chat, who, text);
            tg.sendMessage(chat, "⛔ Этот чат не в списке разрешённых (TELEGRAM_CHAT_ID).");
            return;
        }
        log.info("📩 {} → {}", who, text);

        String[] parts = text.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT).replaceAll("@.*$", "");
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "/start", "/help" -> tg.sendMessage(chat, HELP);
            case "/smoke" -> start(chat, who, "/smoke", "smoke");
            case "/p1" -> start(chat, who, "/p1", "P1");
            case "/p2" -> start(chat, who, "/p2", "P2");
            case "/safe" -> start(chat, who, "/safe", "!creates-order");
            case "/regress" -> start(chat, who, "/regress", "!creates-order & !known-defect");
            case "/defects" -> start(chat, who, "/defects", "known-defect");
            case "/all" -> start(chat, who, "/all", "");
            case "/module" -> {
                if (arg.isBlank()) tg.sendMessage(chat, "Укажите модуль: <code>/module cart</code>\nМодули: " + MODULES);
                else start(chat, who, "/module " + arg, expr(arg));
            }
            case "/id" -> {
                if (arg.isBlank()) tg.sendMessage(chat, "Укажите ID кейсов: <code>/id AUTH-001 CART-006</code>");
                else start(chat, who, "/id " + arg, Arrays.stream(arg.split("[,\\s]+")).map(String::toUpperCase).collect(Collectors.joining(" | ")));
            }
            case "/run" -> {
                if (arg.isBlank()) tg.sendMessage(chat, "Формат: <code>/run P1 &amp; cart</code> (выражение JUnit-тегов)");
                else start(chat, who, "/run " + arg, arg);
            }
            case "/status" -> status(chat);
            case "/stop" -> stop(chat);
            case "/last" -> last(chat);
            default -> tg.sendMessage(chat, "Не понял команду. /help — список команд.");
        }
    }

    private static String expr(String arg) {
        return Arrays.stream(arg.split("[,\\s]+")).map(String::toLowerCase).collect(Collectors.joining(" | "));
    }

    // ---------------------------------------------------------------- запуск

    private synchronized void start(String chat, String who, String label, String groups) {
        if (running.get() != null && running.get().isAlive()) {
            tg.sendMessage(chat, "⏳ Уже идёт прогон <code>" + esc(currentLabel) + "</code>. Дождитесь конца или /stop.");
            return;
        }
        List<String> cmd = new ArrayList<>(List.of("mvn", "-B", "test"));
        if (!groups.isBlank()) cmd.add("-Dgroups=" + groups);
        String shown = groups.isBlank() ? "все тесты" : groups;
        tg.sendMessage(chat, "▶️ <b>Запускаю прогон</b>\nСелектор: <code>" + esc(shown) + "</code>\nИнициатор: " + esc(who)
                + "\nОтчёт придёт сюда, когда закончится.");
        currentLabel = label;
        runStartedAt = System.currentTimeMillis();
        long htmlBefore = lastModified(REPORT_HTML);

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("QA_RUN_LABEL", label);
        pb.environment().put("QA_TRIGGERED_BY", who);
        pb.environment().put("HEADLESS", "true");           // с бота запускаем без окна браузера
        try {
            Process p = pb.start();
            running.set(p);
            synchronized (tail) {
                tail.clear();
            }
            Thread t = new Thread(() -> pump(p, chat, htmlBefore), "run-pump");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            tg.sendMessage(chat, "❌ Не удалось запустить Maven: <code>" + esc(e.getMessage()) + "</code>");
        }
    }

    /** Читает вывод Maven: печатает в консоль бота и запоминает хвост; по завершении сообщает итог. */
    private void pump(Process p, String chat, long htmlBefore) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                System.out.println(line);
                synchronized (tail) {
                    tail.addLast(line);
                    if (tail.size() > 40) tail.removeFirst();
                }
            }
            int code = p.waitFor();
            boolean reportFresh = lastModified(REPORT_HTML) > htmlBefore;
            long secs = (System.currentTimeMillis() - runStartedAt) / 1000;
            if (!reportFresh) {
                // тесты не дошли до отчёта: упала сборка/компиляция либо ни один тест не подошёл под селектор
                String tailText;
                synchronized (tail) {
                    tailText = String.join("\n", tail.stream().filter(l -> !l.isBlank()).skip(Math.max(0, tail.size() - 18)).toList());
                }
                tg.sendMessage(chat, "⚠️ Прогон завершился (код " + code + ", " + secs + " с), но отчёта нет — тесты не запускались.\n"
                        + "Возможно, сборка упала или под селектор не подошёл ни один тест.\n<pre>" + esc(cut(tailText, 2800)) + "</pre>");
            } else if (code != 0) {
                tg.sendMessage(chat, "ℹ️ Maven завершился с кодом " + code + " (в отчёте выше — упавшие тесты).");
            }
        } catch (Exception e) {
            tg.sendMessage(chat, "❌ Ошибка при ожидании прогона: <code>" + esc(String.valueOf(e)) + "</code>");
        } finally {
            running.set(null);
        }
    }

    private void status(String chat) {
        Process p = running.get();
        if (p != null && p.isAlive()) {
            long secs = (System.currentTimeMillis() - runStartedAt) / 1000;
            String last;
            synchronized (tail) {
                last = tail.isEmpty() ? "" : tail.peekLast();
            }
            tg.sendMessage(chat, "⏳ Идёт прогон <code>" + esc(currentLabel) + "</code> уже " + secs + " с.\nПоследняя строка лога:\n<code>"
                    + esc(cut(last, 300)) + "</code>");
        } else {
            tg.sendMessage(chat, "💤 Сейчас ничего не запущено. /last — последний отчёт, /help — команды.");
        }
    }

    private void stop(String chat) {
        Process p = running.get();
        if (p == null || !p.isAlive()) {
            tg.sendMessage(chat, "Нечего останавливать — прогон не идёт.");
            return;
        }
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
        tg.sendMessage(chat, "🛑 Прогон остановлен.");
    }

    private void last(String chat) {
        if (!Files.exists(LAST_TXT)) {
            tg.sendMessage(chat, "Отчётов пока нет — запустите прогон.");
            return;
        }
        tg.sendMessage(chat, "📋 Последний отчёт — см. файл ниже.");
        if (Files.exists(REPORT_HTML)) tg.sendDocument(chat, REPORT_HTML, "📄 Последний прогон");
    }

    private static long lastModified(Path p) {
        try {
            return Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    private static String cut(String s, int max) {
        return s.length() > max ? "…" + s.substring(s.length() - max) : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final String MODULES = "<code>auth header home catalog models search product favorites cart checkout orders profile points wallet notifications requests special master l10n mobile general</code>";

    private static final String HELP = """
            🤖 <b>Aros QA-бот</b>

            <b>Запуск</b>
            /smoke — быстрая проверка ключевых сценариев
            /p1 — все тесты приоритета P1
            /p2 — тесты приоритета P2
            /safe — всё, кроме тестов, создающих заказы
            /regress — регресс: без заказов и без проверок «известных дефектов»
            /defects — только проверки известных дефектов (они падают, пока дефект не исправят)
            /all — абсолютно все тесты (включая создание заказов)
            /module cart — один модуль (можно несколько: <code>/module cart auth</code>)
            /id AUTH-001 CART-006 — конкретные тест-кейсы
            /run P1 &amp; cart — своё выражение из тегов

            <b>Управление</b>
            /status — что сейчас идёт
            /stop — остановить прогон
            /last — прислать последний HTML-отчёт

            Модули: """ + MODULES;
}
