package uz.aros.autotests.tests.storefront.header;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.Header;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Шапка, футер и навигация: логотип, контакты, выбор магазина, иконки, ссылки футера. */
@Epic("Storefront")
@Feature("Шапка и футер")
@Tag("header")
public class HeaderFooterTest extends BaseUiTest {

    @Test
    @Tag("HDR-001")
    @P3
    @DisplayName("HDR-001 · Логотип ведёт на главную")
    void logoLeadsToHome() {
        page.navigate(EnvConfig.baseUrl() + "/cart");
        new Header(page).logo().click();

        page.waitForURL(url -> url.equals(EnvConfig.baseUrl()) || url.equals(EnvConfig.baseUrl() + "/"));
        assertThat(page.locator("a[href$=\"/sections/new\"]").first()).isVisible();
    }

    @Test
    @Tag("HDR-002")
    @P3
    @DisplayName("HDR-002 · В верхней полосе есть телефон и режим работы")
    void topBarShowsPhoneAndWorkingHours() {
        new HomePage(page).open();

        assertThat(page.getByText(Pattern.compile("\\+998\\s?\\d{2}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}")).first()).isVisible();
        assertThat(page.getByText(Pattern.compile("\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2}")).first()).isVisible();
    }

    @Test
    @Tag("HDR-003")
    @P2
    @DisplayName("HDR-003 · Окно выбора магазина открывается и содержит список магазинов")
    void storeDialogOpensWithShopList() {
        new HomePage(page).open();

        Locator dialog = new Header(page).openStoreDialog();

        assertThat(dialog).containsText("Область");
        assertThat(dialog).containsText("Магазины");
        assertThat(dialog.getByRole(AriaRole.BUTTON)).not().hasCount(0);
    }

    @Test
    @Tag("HDR-006")
    @P2
    @DisplayName("HDR-006 · Иконки корзины и входа/профиля присутствуют и кликабельны")
    void headerIconsArePresent() {
        new HomePage(page).open();
        Header header = new Header(page);

        assertThat(header.cartLink()).isVisible();
        assertThat(header.signInButton()).isVisible();
        assertThat(header.catalogButton()).isVisible();
        assertThat(header.modelButton()).isVisible();

        header.cartLink().click();
        page.waitForURL(url -> url.contains("/cart"));
    }

    @Test
    @Tag("HDR-007")
    @P2
    @DisplayName("HDR-007 · Ссылки футера присутствуют; «Публичная оферта» открывается")
    void footerLinksAreReachable() {
        new HomePage(page).open();

        for (String text : new String[]{"О нас", "Сотрудничество", "Политика конфиденциальности", "Публичная оферта"}) {
            Locator link = page.getByRole(AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName(text).setExact(true));
            assertThat(link.first()).isVisible();
            Assertions.assertFalse(link.first().getAttribute("href").isBlank(), "У ссылки «" + text + "» пустой href");
        }
        // Остальные пункты футера сейчас ведут на заглушку /coming-soon — проверяем только оферту.
        String href = page.getByRole(AriaRole.LINK, new com.microsoft.playwright.Page.GetByRoleOptions().setName("Публичная оферта").setExact(true))
                .first().getAttribute("href");
        APIResponse response = page.request().get(href.startsWith("http") ? href : EnvConfig.siteRoot() + href);
        Assertions.assertTrue(response.status() < 400, "Публичная оферта " + href + " отвечает " + response.status());
    }

    @Test
    @Tag("HDR-008")
    @P3
    @DisplayName("HDR-008 · Контакты в футере: телефон, Telegram, почта, адрес")
    void footerContactsArePresent() {
        new HomePage(page).open();

        assertThat(page.locator("a[href^=\"tel:\"]").first()).isVisible();
        assertThat(page.locator("a[href*=\"t.me\"]").first()).isVisible();
        assertThat(page.getByText(Pattern.compile("[\\w.]+@[\\w.]+\\.\\w+")).first()).isVisible();
        assertThat(page.locator("a[href*=\"google.com/maps\"], a[href*=\"yandex\"][href*=\"map\"]").first()).isVisible();
    }

    @Test
    @Tag("MDL-001")
    @P2
    @DisplayName("MDL-001 · «Модель» открывает окно «Выберите бренд» со списком брендов")
    void modelDialogShowsBrands() {
        new HomePage(page).open();

        Locator dialog = new Header(page).openModelDialog();

        assertThat(dialog).containsText("Выберите бренд");
        assertThat(dialog).containsText("Samsung");
        assertThat(dialog).containsText("Apple");
    }

    /** Дополнительная проверка (в Excel не было): гость, нажавший на «сердце» в шапке, должен увидеть приглашение войти. */
    @Test
    @Tag("FAV-X01")
    @P2
    @DisplayName("FAV-X01 · Гость, нажав на «сердце» в шапке, видит окно входа")
    void guestClickingFavoritesSeesSignIn() {
        new HomePage(page).open();
        page.getByRole(AriaRole.BANNER).locator("button").nth(3).click();

        assertThat(page.getByRole(AriaRole.DIALOG)).isVisible();
        assertThat(page.getByRole(AriaRole.DIALOG).getByRole(AriaRole.HEADING)
                .filter(new Locator.FilterOptions().setHasText(uz.aros.autotests.i18n.Text.SIGN_IN.get())).first()).isVisible();
    }
}
