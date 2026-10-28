package uz.aros.autotests.base;

import java.util.regex.Pattern;

/**
 * Мелкие помощники для регулярных выражений в локаторах.
 *
 * <p>Зачем отдельный класс: Playwright передаёт Java-шаблон в браузер, где его выполняет JavaScript, а в JS
 * нет конструкции {@code \Q...\E}, которую использует стандартный {@link Pattern#quote(String)}. Поэтому
 * спецсимволы экранируем вручную.
 */
public final class Regex {

    private Regex() {
    }

    /** Экранирует спецсимволы regex обычными обратными слэшами (понятно и Java, и JavaScript). */
    public static String escape(String text) {
        return text.replaceAll("[\\\\.^$|?*+()\\[\\]{}]", "\\\\$0");
    }

    /** Шаблон «элемент содержит ровно этот текст» (пробелы по краям игнорируются). */
    public static Pattern exact(String text) {
        return Pattern.compile("^\\s*" + escape(text) + "\\s*$");
    }
}
