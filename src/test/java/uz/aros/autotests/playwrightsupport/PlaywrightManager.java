package uz.aros.autotests.playwrightsupport;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import uz.aros.autotests.config.EnvConfig;

/**
 * Отвечает за жизненный цикл объектов Playwright: {@link Playwright} →
 * {@link Browser} → {@link BrowserContext} → {@link Page}.
 *
 * <p>Это "матрёшка", важно понимать, что чем отличается:
 * <ul>
 *   <li>{@link Playwright} — точка входа в библиотеку, запускает
 *       node.js-процесс драйвера "под капотом". Штука тяжёлая, создаём один
 *       раз на класс тестов.</li>
 *   <li>{@link Browser} — сам браузер (Chromium/Firefox/WebKit), один
 *       реальный процесс браузера. Тоже тяжёлый — переиспользуем между
 *       тестами одного класса.</li>
 *   <li>{@link BrowserContext} — изолированная "сессия" внутри браузера:
 *       свои cookies, localStorage, кэш. Примерно как отдельное окно в
 *       режиме инкогнито. Дешёвая в создании — создаём заново перед КАЖДЫМ
 *       тестом, чтобы тесты не влияли друг на друга (например, чтобы логин
 *       в одном тесте не "утёк" в соседний тест).</li>
 *   <li>{@link Page} — одна вкладка внутри контекста. Тесту обычно нужна
 *       только одна вкладка.</li>
 * </ul>
 *
 * <p>Все поля объявлены как {@link ThreadLocal}: если тесты когда-нибудь
 * запустят параллельно (см. junit-platform.properties), у каждого потока
 * будет свой независимый браузер, и потоки не будут "толкать" друг друга
 * за один и тот же объект Page.
 */
public final class PlaywrightManager {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private PlaywrightManager() {
    }

    /** Запускает Playwright и браузер для текущего потока. Вызывается один раз на класс тестов (@BeforeAll). */
    public static void launchBrowser() {
        Playwright playwright = Playwright.create();
        PLAYWRIGHT.set(playwright);

        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(EnvConfig.headless())
                // slowMo замедляет каждое действие Playwright на N миллисекунд.
                // Полезно при локальной отладке с открытым браузером, чтобы
                // успевать глазами следить за происходящим. 0 — без задержки.
                .setSlowMo(0);

        BROWSER.set(playwright.chromium().launch(options));
    }

    /**
     * Создаёт новый изолированный контекст и вкладку для очередного теста.
     * Вызывается перед каждым тестовым методом (@BeforeEach).
     */
    public static Page newPage() {
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(1440, 900)
                // Некоторые элементы сайта зависят от локали браузера
                // (например, форматирование чисел/дат) — фиксируем ru-RU,
                // чтобы поведение было предсказуемым независимо от машины,
                // на которой запускаются тесты.
                .setLocale("ru-RU");

        BrowserContext context = BROWSER.get().newContext(contextOptions);
        // Таймаут по умолчанию для всех операций ожидания элементов в этом
        // контексте (клики, проверки видимости и т.д.), если не указано иное.
        context.setDefaultTimeout(10_000);
        CONTEXT.set(context);

        Page page = context.newPage();
        PAGE.set(page);
        return page;
    }

    /** Текущая вкладка для этого потока — используется в Page Object'ах и в тестах. */
    public static Page currentPage() {
        return PAGE.get();
    }

    /** Закрывает контекст (и вкладку вместе с ним) после теста (@AfterEach). Браузер при этом остаётся открытым. */
    public static void closeContext() {
        if (CONTEXT.get() != null) {
            CONTEXT.get().close();
            CONTEXT.remove();
        }
        PAGE.remove();
    }

    /** Останавливает браузер и Playwright после всех тестов класса (@AfterAll). */
    public static void closeBrowser() {
        if (BROWSER.get() != null) {
            BROWSER.get().close();
            BROWSER.remove();
        }
        if (PLAYWRIGHT.get() != null) {
            PLAYWRIGHT.get().close();
            PLAYWRIGHT.remove();
        }
    }
}
