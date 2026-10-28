package uz.aros.autotests.tests.storefront.catalog;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.pages.storefront.CatalogPage;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.ProductCard;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Дополнительные проверки поиска: по ID, регистр, пустой запрос, фильтры внутри результатов. */
@Epic("Storefront")
@Feature("Поиск товаров")
@Tag("search")
public class SearchExtraTest extends BaseUiTest {

    @Test
    @Tag("SRCH-005")
    @P2
    @DisplayName("SRCH-005 · Поиск по ID товара находит этот товар")
    void searchByProductId() {
        ProductCard source = new HomePage(page).open().firstProductCard();
        String id = source.title().replaceAll(".*\\(ID:\\s*(\\d+)\\).*", "$1");

        CatalogPage catalog = new CatalogPage(page).openWithQuery(id);

        catalog.assertHasResults();
        boolean found = catalog.productCards().stream().anyMatch(c -> c.title().contains("(ID: " + id + ")"));
        Assertions.assertTrue(found, "По запросу «" + id + "» не найден товар с таким ID");
    }

    @Test
    @Tag("SRCH-006")
    @P3
    @DisplayName("SRCH-006 · Поиск запускается и клавишей Enter, и кнопкой-лупой")
    void searchWorksViaEnterAndLoupeButton() {
        HomePage home = new HomePage(page).open();
        home.searchBar().search("akkumulyator");
        page.waitForURL(url -> url.contains("query=akkumulyator"));

        new HomePage(page).open();
        var input = page.getByRole(com.microsoft.playwright.options.AriaRole.BANNER).locator("input[placeholder]");
        input.fill("ekran");
        // Кнопка-лупа — единственная кнопка с классом MuiBox-root после поля ввода
        // (первая кнопка после поля — «Очистить», она появляется, когда в поле есть текст).
        input.locator("xpath=following::button[contains(@class,'MuiBox-root')][1]").click();

        page.waitForURL(url -> url.contains("query=ekran"));
    }

    @Test
    @Tag("SRCH-007")
    @P3
    @DisplayName("SRCH-007 · Пустой запрос и запрос из пробелов не ломают страницу")
    void emptyAndWhitespaceQueries() {
        new HomePage(page).open();
        var input = page.getByRole(com.microsoft.playwright.options.AriaRole.BANNER).locator("input[placeholder]");

        input.fill("   ");
        input.press("Enter");
        page.waitForTimeout(1000);

        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BANNER)).isVisible();
        Assertions.assertFalse(page.url().contains("query=%20%20%20"), "Запрос из одних пробелов ушёл в поиск: " + page.url());
    }

    @Test
    @Tag("SRCH-008")
    @P3
    @DisplayName("SRCH-008 · Регистр запроса не влияет на результаты")
    void queryCaseDoesNotMatter() {
        CatalogPage lower = new CatalogPage(page).openWithQuery("akkumulyator");
        int lowerCount = lower.cardsCount();

        CatalogPage upper = new CatalogPage(page).openWithQuery("AKKUMULYATOR");

        Assertions.assertEquals(lowerCount, upper.cardsCount(), "Разный регистр дал разное число результатов");
    }

    @Test
    @Tag("SRCH-009")
    @P3
    @DisplayName("SRCH-009 · В результатах поиска работают фильтры (бренд попадает в URL, поисковая фраза остаётся)")
    void filtersWorkInsideSearchResults() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery("akkumulyator");

        catalog.filterCheckbox("Samsung").check();
        catalog.settle();

        Assertions.assertTrue(page.url().contains("query=akkumulyator") && page.url().contains("manufacture_brand="),
                "После фильтра в URL нет поиска и бренда: " + page.url());
        catalog.assertHasResults();
    }
}
