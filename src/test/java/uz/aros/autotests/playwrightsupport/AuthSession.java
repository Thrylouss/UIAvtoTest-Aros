package uz.aros.autotests.playwrightsupport;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.HomePage;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Разовый вход тестовым пользователем с сохранением сессии в файл.
 *
 * <p><b>Идея.</b> Входить через форму перед КАЖДЫМ тестом — медленно (+5–8 секунд) и нагружает staging
 * (он и так отвечает 429 при частых запросах). Поэтому: один раз за прогон логинимся в отдельном контексте,
 * сохраняем «storage state» (cookies + localStorage, где сайт держит токен) в {@code target/auth/user.json}
 * и затем открываем каждый тест с этим файлом — тест сразу «залогинен».
 *
 * <p>Сам процесс входа здесь — тот же UI-сценарий, что проверяет LoginTest, поэтому если вход сломается,
 * упадут и сам LoginTest, и все тесты, которым нужна авторизация (с понятной причиной).
 */
public final class AuthSession {

    private static final Logger log = LoggerFactory.getLogger("QA");
    private static final Path STATE_FILE = Path.of("target", "auth", "user.json");

    private static volatile boolean ready;

    private AuthSession() {
    }

    /** Возвращает путь к файлу сессии; при первом вызове (и если файла нет) выполняет вход. */
    public static synchronized Path userState() {
        if (ready && Files.exists(STATE_FILE)) {
            return STATE_FILE;
        }
        try {
            Files.createDirectories(STATE_FILE.getParent());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось создать папку для сессии: " + e.getMessage(), e);
        }
        log.info("🔐 Вход тестовым пользователем (один раз за прогон), сессия будет переиспользована…");
        BrowserContext context = PlaywrightManager.browser().newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                .setViewportSize(1440, 900).setLocale("ru-RU"));
        context.setDefaultTimeout(15_000);
        try {
            Page page = context.newPage();
            HomePage home = new HomePage(page).open();
            home.openSignInForm().signInWithPassword(EnvConfig.testUserPhone(), EnvConfig.testUserPassword());
            home.assertUserIsLoggedIn();
            // ВАЖНО: после входа сайт сам переключает язык на «en» (пишет "language":"en" в localStorage) и
            // перекидывает на /en. Если сохранить сессию как есть, ВСЕ следующие тесты откроют /ru, но увидят
            // английский интерфейс (сайт доверяет localStorage больше, чем адресу). Поэтому возвращаем нужный
            // язык вручную перед сохранением. (Сам факт смены языка при входе — отдельный дефект, см. AUTH-X01.)
            page.evaluate("lang => localStorage.setItem('language', JSON.stringify(lang))", EnvConfig.locale());
            context.storageState(new BrowserContext.StorageStateOptions().setPath(STATE_FILE));
            // Корзина пользователя хранится на сервере и переживает прогоны. Если прошлый прогон оборвали
            // (Ctrl+C, падение), в ней могли остаться товары — тогда у первой карточки на главной вместо
            // кнопки «в корзину» уже счётчик, и тесты падают «на ровном месте». Поэтому один раз чистим корзину.
            try {
                new uz.aros.autotests.pages.storefront.CartPage(page).open().clear();
            } catch (RuntimeException e) {
                log.warn("Не удалось очистить корзину перед прогоном: {}", e.getMessage());
            }
            ready = true;
            log.info("🔐 Вход выполнен, сессия сохранена: {}", STATE_FILE);
        } finally {
            context.close();
        }
        return STATE_FILE;
    }
}
