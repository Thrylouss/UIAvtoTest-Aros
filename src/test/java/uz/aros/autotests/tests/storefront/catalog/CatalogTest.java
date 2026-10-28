package uz.aros.autotests.tests.storefront.catalog;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.i18n.Text;
import uz.aros.autotests.pages.storefront.CatalogPage;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.Header;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Каталог: меню категорий, фильтры, сортировка, «Показать ещё». Для стабильности работаем с выдачей по запросу «akkumulyator». */
@Epic("Storefront")
@Feature("Каталог и фильтры")
@Tag("catalog")
public class CatalogTest extends BaseUiTest {

    private static final String QUERY = "akkumulyator";

    @Test
    @Tag("CAT-001")
    @P1
    @DisplayName("CAT-001 · Меню «Каталог» открывается и показывает категории")
    void catalogMenuOpensWithCategories() {
        new HomePage(page).open();

        new Header(page).openCatalogMenu();

        assertThat(page.getByText(Pattern.compile("\\d+\\s+раздел")).first()).isVisible();
    }

    @Test
    @Tag("CAT-007")
    @P1
    @DisplayName("CAT-007 · Тумблер «Только в наличии» включён по умолчанию и переключается")
    void inStockToggleSwitches() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);

        assertThat(catalog.onlyInStockToggle()).isChecked();
        catalog.onlyInStockToggle().uncheck();
        catalog.settle();
        assertThat(catalog.onlyInStockToggle()).not().isChecked();
        catalog.assertHasResults();
    }

    @Test
    @Tag("CAT-008")
    @P2
    @DisplayName("CAT-008 · «Очистить все фильтры» сбрасывает выбранные фильтры")
    void clearAllFiltersResetsSelection() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);
        assertThat(catalog.clearFiltersButton()).isDisabled();

        catalog.filterCheckbox("Samsung").check();
        catalog.settle();
        assertThat(catalog.clearFiltersButton()).isEnabled();
        Assertions.assertTrue(page.url().contains("manufacture_brand="), "Выбранный бренд не попал в URL: " + page.url());

        catalog.clearFiltersButton().click();
        catalog.settle();

        assertThat(catalog.filterCheckbox("Samsung")).not().isChecked();
        Assertions.assertFalse(page.url().contains("manufacture_brand="), "После сброса бренд остался в URL: " + page.url());
    }

    @Test
    @Tag("CAT-009")
    @P2
    @DisplayName("CAT-009 · Сортировка «Сначала дешевые» упорядочивает цены по возрастанию")
    void sortingCheapFirst() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);

        catalog.sortBy(Text.SORT_CHEAP_FIRST);

        List<Long> prices = catalog.visiblePrices();
        List<Long> sorted = new ArrayList<>(prices);
        sorted.sort(Comparator.naturalOrder());
        Assertions.assertEquals(sorted, prices, "Цены не по возрастанию: " + prices);
    }

    @Test
    @Tag("CAT-010")
    @P2
    @DisplayName("CAT-010 · Сортировка «Сначала дорогие» упорядочивает цены по убыванию")
    void sortingExpensiveFirst() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);

        catalog.sortBy(Text.SORT_EXPENSIVE_FIRST);

        List<Long> prices = catalog.visiblePrices();
        List<Long> sorted = new ArrayList<>(prices);
        sorted.sort(Comparator.reverseOrder());
        Assertions.assertEquals(sorted, prices, "Цены не по убыванию: " + prices);
    }

    @Test
    @Tag("CAT-012")
    @P2
    @DisplayName("CAT-012 · «Показать ещё» подгружает следующую порцию товаров")
    void showMoreLoadsNextPage() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);
        int before = catalog.cardsCount();

        catalog.showMoreButton().click();

        page.waitForCondition(() -> catalog.cardsCount() > before);
        Assertions.assertTrue(catalog.cardsCount() > before, "После «Показать ещё» число карточек не выросло: было " + before);
    }

    @Test
    @Tag("CAT-015")
    @P3
    @DisplayName("CAT-015 · Выбранный фильтр сохраняется после перезагрузки страницы")
    void filtersSurviveReload() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);
        catalog.filterCheckbox("Samsung").check();
        catalog.settle();

        page.reload();
        new CatalogPage(page).waitUntilLoaded();

        assertThat(catalog.filterCheckbox("Samsung")).isChecked();
    }

    @Test
    @Tag("CAT-016")
    @P3
    @DisplayName("CAT-016 · Пустая выдача: сообщение и кнопка «Показать все»")
    void emptyResultShowsMessageAndShowAll() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery("zzzqqqxxx" + System.nanoTime() % 1000);

        catalog.assertNoResults();
        assertThat(page.getByText(Text.SHOW_ALL.get()).first()).isVisible();
    }

    @Test
    @Tag("CAT-017")
    @P3
    @DisplayName("CAT-017 · Хлебные крошки на странице товара ведут на главную")
    void breadcrumbsLeadHome() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);
        catalog.productCards().get(0).open();
        page.waitForURL(url -> url.contains("/product/"));
        new uz.aros.autotests.pages.storefront.ProductPage(page).title().waitFor();
        // Даём странице «ожить» (гидратация React): клик по ссылке до этого момента не срабатывает.
        page.waitForTimeout(1500);

        var homeLink = page.locator("a[href=\"" + java.net.URI.create(uz.aros.autotests.config.EnvConfig.baseUrl()).getPath() + "\"]")
                .filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText(Text.HOME_BREADCRUMB.get())).first();
        homeLink.click();

        page.waitForURL(url -> url.equals(uz.aros.autotests.config.EnvConfig.baseUrl()) || url.endsWith("/ru/") || url.endsWith("/ru"));
    }

    /** Найдено при автоматизации: в меню сортировки на /ru опечатка «Сначало» вместо «Сначала». */
    @Test
    @Tag("CAT-X02")
    @Tag("known-defect")
    @P3
    @DisplayName("CAT-X02 · Пункты меню сортировки без орфографических ошибок")
    void sortMenuHasNoTypos() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);

        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName(Text.SORT.get())).click();
        page.getByText(Text.SORT_CHEAP_FIRST.get()).first().waitFor();

        Assertions.assertEquals(0, page.getByText("Сначало").count(),
                "ДЕФЕКТ: в меню сортировки опечатка «Сначало …» вместо «Сначала …»");
    }

    /** Найдено при автоматизации: товар «под заказ» показывается с ценой «0 сум», хотя включён «Только в наличии». */
    @Test
    @Tag("CAT-X01")
    @Tag("known-defect")
    @P2
    @DisplayName("CAT-X01 · В выдаче нет товаров с ценой «0 сум»")
    void noZeroPriceProductsInResults() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);

        List<String> zero = catalog.productCards().stream().filter(c -> c.currentPrice() == 0).map(c -> c.title()).toList();

        Assertions.assertTrue(zero.isEmpty(), "ДЕФЕКТ: в каталоге показаны товары с ценой 0 сум: " + zero);
    }

    @Test
    @Tag("CAT-004")
    @P1
    @DisplayName("CAT-004 · Фильтр по цене «от»: в выдаче остаются только товары не дешевле заданной суммы")
    void priceFromFilterWorks() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);
        catalog.settle();

        catalog.priceInputs().first().fill("100000");
        catalog.priceInputs().first().press("Enter");
        catalog.priceInputs().first().blur();
        catalog.settle();
        page.waitForTimeout(1500);

        List<Long> prices = catalog.visiblePrices();
        Assertions.assertFalse(prices.isEmpty(), "После фильтра цены «от 100 000» выдача пуста");
        Assertions.assertTrue(prices.stream().allMatch(price -> price >= 100_000),
                "В выдаче остались товары дешевле 100 000: " + prices);
    }

    @Test
    @Tag("CAT-006")
    @P3
    @DisplayName("CAT-006 · Цена «от» больше цены «до» не ломает страницу")
    void priceFromGreaterThanToDoesNotBreakPage() {
        CatalogPage catalog = new CatalogPage(page).openWithQuery(QUERY);
        catalog.settle();

        catalog.priceInputs().first().fill("999999999");
        catalog.priceInputs().first().press("Enter");
        catalog.priceInputs().first().blur();
        page.waitForTimeout(2000);

        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.BANNER)).isVisible();
        Assertions.assertFalse(page.locator("body").innerText().matches("(?s).*(undefined|NaN|Infinity).*"),
                "На странице видны технические значения undefined/NaN");
    }
}
