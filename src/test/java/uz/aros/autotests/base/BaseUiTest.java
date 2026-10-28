package uz.aros.autotests.base;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import uz.aros.autotests.extensions.DiagnosticsExtension;
import uz.aros.autotests.playwrightsupport.PlaywrightManager;

/**
 * Родительский класс для ВСЕХ UI-тестов проекта.
 *
 * <p>Здесь и только здесь описан жизненный цикл браузера — конкретные
 * тестовые классы (например, {@code LoginTest}) ничего не знают о
 * Playwright напрямую, они просто наследуются от {@code BaseUiTest} и
 * получают готовое поле {@link #page}.
 *
 * <p>Порядок вызовов JUnit5 для одного тестового класса с двумя тестами
 * выглядит так:
 * <pre>
 *   launchBrowser()          (@BeforeAll — один раз)
 *     openNewPage()           (@BeforeEach — перед тестом 1)
 *       тест 1
 *     closeContext()          (@AfterEach — после теста 1)
 *     openNewPage()           (@BeforeEach — перед тестом 2)
 *       тест 2
 *     closeContext()          (@AfterEach — после теста 2)
 *   closeBrowser()            (@AfterAll — один раз)
 * </pre>
 * Так браузер запускается только один раз на класс (это дорогая операция),
 * а каждый тест получает чистый, изолированный контекст (дешёвая операция).
 *
 * <p>{@code @ExtendWith(DiagnosticsExtension.class)} подключает
 * сбор диагностики (скриншот, сетевые запросы, ошибки консоли) при падении любого теста-наследника.
 */
@ExtendWith(DiagnosticsExtension.class)
public abstract class BaseUiTest {

    /**
     * protected — тестовые классы-наследники (например, LoginTest) читают
     * и используют это поле напрямую, как обычное унаследованное поле
     * (просто пишут {@code page.navigate(...)}).
     *
     * <p>А вот {@link DiagnosticsExtension} наследником не является
     * (это отдельный, несвязанный класс в соседнем пакете), поэтому для него
     * напрямую поле недоступно — правила Java запрещают доступ к protected
     * через ссылку типа "суперкласс" из чужого класса. Для него ниже есть
     * отдельный публичный метод {@link #getPage()}.
     */
    protected Page page;

    /** Публичный доступ к текущей странице — нужен внешним классам вроде {@link DiagnosticsExtension}. */
    public Page getPage() {
        return page;
    }

    @BeforeAll
    static void launchBrowser() {
        PlaywrightManager.launchBrowser();
    }

    @BeforeEach
    void openNewPage() {
        page = createPage();
    }

    /**
     * Как именно создать страницу для теста. По умолчанию — «гость» на десктопе; наследники переопределяют
     * этот метод: {@link AuthenticatedUiTest} (залогиненный), {@link MobileUiTest} (телефон) и т.д.
     */
    protected Page createPage() {
        return PlaywrightManager.newPage();
    }

    @AfterEach
    void closeContext() {
        PlaywrightManager.closeContext();
    }

    @AfterAll
    static void closeBrowser() {
        PlaywrightManager.closeBrowser();
    }
}
