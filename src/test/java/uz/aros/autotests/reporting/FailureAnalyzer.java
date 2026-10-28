package uz.aros.autotests.reporting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Превращает «простыню» исключения Playwright/JUnit в понятную причину падения.
 *
 * <p>Сырое сообщение Playwright длинное, с технической трассировкой и call log. Для отчёта нам нужно три вещи:
 * <b>что произошло</b> простыми словами, <b>технические детали</b> (ожидали/получили) и
 * <b>какой элемент</b> искали.
 */
public final class FailureAnalyzer {

    private static final Pattern PW_MESSAGE = Pattern.compile("message='([^\\n]*?)'?\\s*(?:\\n|$)");

    /** @param headline что произошло (по-русски); @param detail технические детали; @param element локатор; @param location место в тесте */
    public record Reason(String headline, String detail, String element, String location) {
        public static Reason of(String headline) {
            return new Reason(headline, "", "", "");
        }
    }

    private FailureAnalyzer() {
    }

    public static Reason analyze(Throwable t) {
        if (t == null) return Reason.of("Причина неизвестна");
        String raw = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();

        // Playwright кладёт текст в формате "Error { message='...' ... }" — достаём сам message
        Matcher pw = PW_MESSAGE.matcher(raw);
        String body = raw;
        if (raw.startsWith("Error {") && pw.find()) {
            body = pw.group(1) + (raw.contains("Call log:") ? "\nCall log:" + raw.substring(raw.indexOf("Call log:") + 9) : "");
        }

        String[] parts = body.split("Call log:", 2);
        String head = parts[0].strip();
        String callLog = parts.length > 1 ? parts[1] : "";

        String element = "";
        Matcher w = Pattern.compile("waiting for ([^\\n]+)").matcher(callLog);
        if (w.find()) element = shorten(w.group(1).strip(), 240);

        String headline = classify(t, head, callLog);
        String detail = shorten(head.replaceAll("\\s+", " "), 320);
        // Если headline уже сказал всё, дублировать «Timeout 10000ms exceeded.» незачем
        if (detail.equalsIgnoreCase(headline)) detail = "";
        return new Reason(headline, detail, element, location(t));
    }

    private static String classify(Throwable t, String head, String callLog) {
        String h = head.toLowerCase();
        String n = t.getClass().getName();
        if (h.contains("strict mode violation")) return "Локатор нашёл несколько элементов вместо одного";
        if (h.contains("to be visible") || h.contains("expected to be visible")) return "Элемент не появился на странице";
        if (h.contains("to be hidden") || h.contains("not to be visible")) return "Элемент остался на странице, хотя должен был исчезнуть";
        if (h.contains("to have text") || h.contains("to contain text") || h.contains("containtext") || h.contains("have text"))
            return "Текст на странице не совпал с ожидаемым";
        if (h.contains("to have value")) return "Значение поля не совпало с ожидаемым";
        if (h.contains("to have count")) return "Количество элементов не совпало с ожидаемым";
        if (h.contains("to have url") || h.contains("waitforurl")) return "Адрес страницы не совпал с ожидаемым";
        if (h.contains("to have attribute")) return "Атрибут элемента не совпал с ожидаемым";
        if (callLog.contains("intercepts pointer events")) return "Клик не прошёл: элемент перекрыт другим (всплывающее окно/тост)";
        if (n.contains("TimeoutError") || h.contains("timeout")) {
            Matcher m = Pattern.compile("(\\d+)\\s*ms").matcher(head);
            return "Не дождались элемента или действия" + (m.find() ? " за " + m.group(1) + " мс" : "");
        }
        if (n.contains("AssertionFailedError") || n.contains("AssertionError")) return "Проверка не прошла";
        if (h.contains("err_internet_disconnected") || h.contains("err_name_not_resolved")) return "Нет сети / сайт недоступен";
        if (h.contains("err_connection") || h.contains("net::err")) return "Сетевая ошибка при открытии страницы";
        if (n.contains("IndexOutOfBounds")) return "На странице меньше элементов, чем ожидалось";
        if (n.contains("NullPointer")) return "Ошибка в коде теста (NullPointer)";
        return "Ошибка: " + t.getClass().getSimpleName();
    }

    /** Первая строка стека внутри тестов проекта: {@code CartTest.java:73}. */
    static String location(Throwable t) {
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().startsWith("uz.aros.autotests.tests")) {
                return e.getFileName() + ":" + e.getLineNumber();
            }
        }
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().startsWith("uz.aros.autotests")) {
                return e.getFileName() + ":" + e.getLineNumber();
            }
        }
        return "";
    }

    private static String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
