package uz.aros.autotests.reporting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Расшифровка префиксов ID тест-кейсов (AUTH-001 → «Авторизация и сессия») — такие же названия модулей, как в Excel. */
public final class ModuleNames {

    /** ID кейса: AUTH-001 или дополнительная проверка AUTH-X01 (добавлена автором автотестов, в Excel её не было). */
    public static final Pattern CASE_ID = Pattern.compile("^([A-Z0-9]{2,5})-(X?\\d{2,3})$");

    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("AUTH", "Авторизация и сессия");
        NAMES.put("HDR", "Шапка, футер и навигация");
        NAMES.put("HOME", "Главная страница");
        NAMES.put("CAT", "Каталог и фильтры");
        NAMES.put("MDL", "Подбор по модели устройства");
        NAMES.put("SRCH", "Поиск");
        NAMES.put("PDP", "Карточка товара");
        NAMES.put("FAV", "Избранное");
        NAMES.put("CART", "Корзина");
        NAMES.put("CHK", "Оформление заказа");
        NAMES.put("ORD", "Мои заказы");
        NAMES.put("PRF", "Личные данные и адреса");
        NAMES.put("PTS", "Баллы");
        NAMES.put("WLT", "Мой кошелёк (долг)");
        NAMES.put("NTF", "Уведомления");
        NAMES.put("REQ", "Мои запросы");
        NAMES.put("SPC", "Спецзаказ");
        NAMES.put("MST", "Роль «Мастер»");
        NAMES.put("L10N", "Локализация");
        NAMES.put("MOB", "Мобильная версия");
        NAMES.put("GEN", "Общие проверки");
        NAMES.put("ADM", "Админ-панель");
    }

    private ModuleNames() {
    }

    /** Возвращает ID кейса, если строка похожа на него, иначе null. */
    public static String asCaseId(String tag) {
        return tag != null && CASE_ID.matcher(tag).matches() ? tag : null;
    }

    public static String moduleOf(String caseId) {
        if (caseId == null) return "Прочее";
        Matcher m = CASE_ID.matcher(caseId);
        return m.matches() ? NAMES.getOrDefault(m.group(1), m.group(1)) : "Прочее";
    }
}
