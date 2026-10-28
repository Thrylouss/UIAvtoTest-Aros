package uz.aros.autotests.config;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Единая точка чтения конфигурации теста: URL окружения, тестовые учётные
 * данные, флаг headless и т.д.
 *
 * <p>Почему всё собрано в одном классе, а не разбросано по тестам? Потому что
 * значения приходят из ДВУХ источников, и логику их совмещения нужно
 * написать только один раз:
 * <ol>
 *   <li>Локально — из файла {@code .env} в корне проекта (через библиотеку
 *       dotenv-java). Файл в {@code .gitignore}, поэтому реальные пароли
 *       никогда не попадают в git — в репозитории лежит только
 *       {@code .env.example} с пустыми/примерными значениями.</li>
 *   <li>На CI (GitHub Actions) — из переменных окружения (Secrets),
 *       .env-файла там просто нет.</li>
 * </ol>
 *
 * <p>Правило приоритета: если переменная задана в окружении процесса
 * (System.getenv) — используем её, иначе смотрим в .env. Так один и тот же
 * код работает и локально, и в CI без каких-либо if'ов в самих тестах.
 */
public final class EnvConfig {

    /**
     * dotenv загружается один раз при первом обращении к классу (статическое
     * поле инициализируется лениво — при загрузке класса JVM).
     * ignoreIfMissing() — не бросать исключение, если .env отсутствует
     * (актуально для CI, там его и не должно быть).
     */
    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    // Приватный конструктор запрещает создавать экземпляры класса снаружи:
    // EnvConfig — это просто набор статических методов ("utility class"),
    // создавать объект `new EnvConfig()` для него не имеет смысла.
    private EnvConfig() {
    }

    /**
     * Базовый метод чтения: сначала переменные окружения ОС, потом .env.
     * Возвращает null, если значение нигде не найдено.
     */
    private static String read(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return DOTENV.get(key);
    }

    /**
     * То же самое, но с значением по умолчанию — удобно для необязательных
     * настроек вроде HEADLESS, у которых есть разумное значение "из коробки".
     */
    private static String readOrDefault(String key, String defaultValue) {
        String value = read(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * Для обязательных значений (например, пароль тестового пользователя):
     * если их нет — тест не должен молча упасть на NullPointerException
     * где-то в середине сценария, а должен сразу и понятно сказать, чего
     * не хватает и что с этим делать.
     */
    private static String readRequired(String key) {
        String value = read(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Не найдена обязательная переменная окружения '" + key + "'. "
                            + "Скопируйте .env.example в .env и заполните значение, "
                            + "либо задайте переменную окружения в CI.");
        }
        return value;
    }

    /** Базовый URL тестируемого окружения, например https://staging.aros.uz/ru. */
    public static String baseUrl() {
        return readOrDefault("BASE_URL", "https://staging.aros.uz/ru");
    }

    /** Телефон валидного тестового пользователя для позитивных сценариев логина. */
    public static String testUserPhone() {
        return readRequired("TEST_USER_PHONE");
    }

    /** Пароль валидного тестового пользователя. */
    public static String testUserPassword() {
        return readRequired("TEST_USER_PASSWORD");
    }

    /** Заведомо неверный, но валидный по формату пароль — для негативных кейсов. */
    public static String testUserWrongPassword() {
        return readOrDefault("TEST_USER_WRONG_PASSWORD", "WrongPass123");
    }

    /** Базовый URL admin-панели. */
    public static String adminBaseUrl() {
        return readOrDefault("ADMIN_BASE_URL", "https://staging-admin.aros.uz");
    }

    /** Логин администратора (заполняется только если нужны admin-тесты). */
    public static String adminUsername() {
        return readRequired("ADMIN_USERNAME");
    }

    /** Пароль администратора. */
    public static String adminPassword() {
        return readRequired("ADMIN_PASSWORD");
    }

    /**
     * Запускать ли браузер в headless-режиме (без видимого окна).
     * true — по умолчанию (нужно для CI, там нет экрана);
     * локально можно временно поставить HEADLESS=false в .env, чтобы
     * визуально наблюдать за тестом во время отладки.
     *
     * <p>Также можно переопределить через флаг Maven без правки .env:
     * {@code mvn test -Dheadless=false}.
     */
    public static boolean headless() {
        String systemProperty = System.getProperty("headless");
        if (systemProperty != null && !systemProperty.isBlank()) {
            return Boolean.parseBoolean(systemProperty);
        }
        return Boolean.parseBoolean(readOrDefault("HEADLESS", "true"));
    }

    // ------------------------------------------------------------------ Telegram и режим запуска

    /** Любая необязательная настройка: переменная окружения / .env / системное свойство (-Dkey=value). */
    public static String optional(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        String value = read(key);
        return value == null ? "" : value.trim();
    }

    /** Токен бота от @BotFather. Пусто — Telegram-отчёты выключены (всё остальное работает). */
    public static String telegramToken() {
        return optional("TELEGRAM_BOT_TOKEN");
    }

    /** Чаты, в которые шлём отчёты и от которых бот принимает команды (через запятую). */
    public static java.util.List<String> telegramChatIds() {
        String raw = optional("TELEGRAM_CHAT_ID");
        if (raw.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(raw.split("[,;\\s]+")).filter(x -> !x.isBlank()).toList();
    }

    public static boolean telegramEnabled() {
        return !telegramToken().isBlank() && !telegramChatIds().isEmpty()
                && !"true".equalsIgnoreCase(optional("QA_NO_TELEGRAM"));
    }

    /** Подпись запуска, которую бот передаёт в отчёт («/smoke», «/id AUTH-001»…). */
    public static String runLabel() {
        return optional("QA_RUN_LABEL");
    }

    /** Кто запустил прогон (бот подставляет имя пользователя Telegram). */
    public static String triggeredBy() {
        return optional("QA_TRIGGERED_BY");
    }

    /** Язык интерфейса, на котором идёт прогон: ru / uz / en. По умолчанию берётся из BASE_URL (…/ru). */
    public static String locale() {
        String explicit = optional("LOCALE");
        if (!explicit.isBlank()) {
            return explicit.toLowerCase();
        }
        String path = java.net.URI.create(baseUrl()).getPath();
        for (String l : new String[]{"ru", "uz", "en"}) {
            if (path != null && (path.equals("/" + l) || path.startsWith("/" + l + "/"))) {
                return l;
            }
        }
        return "ru";
    }

    /** Корень сайта без языка: https://staging.aros.uz */
    public static String siteRoot() {
        java.net.URI u = java.net.URI.create(baseUrl());
        return u.getScheme() + "://" + u.getAuthority();
    }
}
