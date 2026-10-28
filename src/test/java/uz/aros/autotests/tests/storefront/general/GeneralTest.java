package uz.aros.autotests.tests.storefront.general;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.reporting.ApiCall;
import uz.aros.autotests.reporting.DiagnosticsRegistry;
import uz.aros.autotests.reporting.TestDiagnostics;

import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Сквозные технические проверки: 404, серверные ошибки (5xx) и заголовки вкладок на ключевых страницах. */
@Epic("Storefront")
@Feature("Общие проверки")
@Tag("general")
public class GeneralTest extends BaseUiTest {

    @Test
    @Tag("GEN-001")
    @P3
    @DisplayName("GEN-001 · Несуществующий адрес показывает страницу 404 со ссылкой на главную")
    void notFoundPage() {
        TestDiagnostics diagnostics = DiagnosticsRegistry.current();
        if (diagnostics != null) {
            diagnostics.ignoreStatus(404);
        }
        page.navigate(EnvConfig.baseUrl() + "/this-page-does-not-exist-" + System.nanoTime() % 1000);

        page.waitForTimeout(1500);
        Assertions.assertFalse(page.locator("body").innerText().isBlank(), "Страница 404 пустая");
        assertThat(page.locator("a[href=\"" + java.net.URI.create(EnvConfig.baseUrl()).getPath() + "\"], a[href=\"/\"]").first())
                .isVisible();
    }

    @ParameterizedTest(name = "GEN-002 · {0}")
    @ValueSource(strings = {"", "/sections/new", "/filter?query=akkumulyator", "/cart"})
    @Tag("GEN-002")
    @P2
    @DisplayName("GEN-002 · На ключевых страницах нет серверных ошибок 5xx")
    void noServerErrorsOnKeyPages(String path) {
        page.navigate(EnvConfig.baseUrl() + path);
        page.waitForTimeout(3500);

        TestDiagnostics diagnostics = DiagnosticsRegistry.current();
        Assertions.assertNotNull(diagnostics, "Диагностика не подключена — нельзя проверить сетевые ошибки");
        List<ApiCall> serverErrors = diagnostics.problems().stream().filter(ApiCall::isServerError)
                .filter(c -> !c.url().contains("/monitoring")).toList();
        Assertions.assertTrue(serverErrors.isEmpty(),
                "Страница " + path + " получила ответы 5xx: " + serverErrors.stream().map(ApiCall::oneLine).toList());
    }

    @ParameterizedTest(name = "GEN-003 · {0}")
    @ValueSource(strings = {"", "/cart", "/sections/onsale", "/filter?query=akkumulyator"})
    @Tag("GEN-003")
    @P3
    @DisplayName("GEN-003 · У страниц осмысленный заголовок вкладки (title)")
    void pageTitlesAreMeaningful(String path) {
        page.navigate(EnvConfig.baseUrl() + path);
        page.waitForCondition(() -> !page.title().isBlank());

        String title = page.title().trim();
        Assertions.assertTrue(Pattern.compile("(?i)aros").matcher(title).find(), "В title нет названия сайта: «" + title + "»");
        Assertions.assertFalse(title.startsWith("-") || title.startsWith("|"),
                "Title начинается с разделителя (пустая подстановка названия): «" + title + "»");
    }
}
