package uz.aros.autotests.tests.storefront.l10n;

import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.base.AuthenticatedUiTest;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.Header;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Языки интерфейса: переключатель, сохранение выбора, непереведённые строки. */
@Epic("Storefront")
@Feature("Локализация")
@Tag("l10n")
public class LocalizationTest extends BaseUiTest {

    @Test
    @Tag("L10N-001")
    @P2
    @DisplayName("L10N-001 · Переключатель языка меняет язык интерфейса (Ru → En)")
    void languageSwitcherChangesLanguage() {
        new HomePage(page).open();

        new Header(page).languageButton().click();
        page.getByRole(AriaRole.MENUITEM).filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("En")).first().click();

        page.waitForURL(url -> url.contains("/en"));
        assertThat(page.getByText("Catalog").first()).isVisible();
    }

    @Test
    @Tag("L10N-002")
    @P2
    @DisplayName("L10N-002 · Выбранный язык сохраняется при навигации и перезагрузке")
    void selectedLanguageIsKept() {
        new HomePage(page).open();
        new Header(page).languageButton().click();
        page.getByRole(AriaRole.MENUITEM).filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("En")).first().click();
        page.waitForURL(url -> url.contains("/en"));

        page.locator("a[href$=\"/sections/new\"]").first().click();
        page.waitForURL(url -> url.contains("/sections/new"));
        Assertions.assertTrue(page.url().contains("/en/"), "После перехода по ссылке язык сменился: " + page.url());

        page.reload();
        Assertions.assertTrue(page.url().contains("/en/"), "После перезагрузки язык сменился: " + page.url());
    }

    @Test
    @Tag("L10N-X01")
    @Tag("known-defect")
    @P2
    @DisplayName("L10N-X01 · Фильтр цены в каталоге на /ru не содержит узбекских подписей «so'm»")
    void catalogPriceFilterLabelIsLocalized() {
        new uz.aros.autotests.pages.storefront.CatalogPage(page).openWithQuery("akkumulyator");

        String filters = page.locator("body").innerText();
        Assertions.assertFalse(filters.contains("so'm") || filters.contains("so’m"),
                "ДЕФЕКТ перевода: в русской версии каталога подпись «Цена (so'm)» — на узбекском");
    }

    @Test
    @Tag("L10N-X02")
    @Tag("known-defect")
    @P2
    @DisplayName("L10N-X02 · Поле поиска в шапке на /ru имеет русский placeholder на всех страницах")
    void searchPlaceholderIsRussianOnCart() {
        page.navigate(EnvConfig.baseUrl() + "/cart");

        String placeholder = page.getByRole(AriaRole.BANNER).locator("input[placeholder]").getAttribute("placeholder");
        Assertions.assertTrue(placeholder != null && placeholder.matches(".*[А-Яа-я].*"),
                "ДЕФЕКТ перевода: placeholder поиска на /ru/cart — «" + placeholder + "» (ожидался русский)");
    }
}
