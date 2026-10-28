package uz.aros.autotests.tests.storefront.auth;

import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.i18n.Text;
import uz.aros.autotests.pages.storefront.CartPage;
import uz.aros.autotests.pages.storefront.HomePage;

/** Что видит и может делать НЕавторизованный пользователь: закрытые страницы и оформление заказа. */
@Epic("Storefront")
@Feature("Авторизация")
@Tag("auth")
public class GuestAccessTest extends BaseUiTest {

    @ParameterizedTest(name = "AUTH-014 · Гость на {0}")
    @ValueSource(strings = {"/profile/account", "/profile/orders", "/confirmation"})
    @Tag("AUTH-014")
    @P2
    @DisplayName("AUTH-014 · Закрытые страницы не показывают данные без авторизации")
    void privatePagesShowNoDataToGuest(String path) {
        page.navigate(EnvConfig.baseUrl() + path);
        page.waitForTimeout(3000);

        String body = page.url().equals("about:blank") ? "" : page.locator("body").innerText();
        Assertions.assertFalse(body.contains("Личные данные") || body.contains("Номер заказа") || body.contains("Подтверждение заказа"),
                "Гость увидел содержимое закрытой страницы " + path + " (URL: " + page.url() + ")");
    }

    /**
     * Дополнительная проверка (в Excel её не было). Найдено при автоматизации: гость, открывший закрытую страницу,
     * не получает ни окна входа, ни переадресации — браузер остаётся на пустой странице about:blank.
     */
    @Test
    @Tag("AUTH-X03")
    @Tag("known-defect")
    @P2
    @DisplayName("AUTH-X03 · Гостя со закрытой страницы переводят на вход/главную, а не оставляют на пустом экране")
    void guestOnPrivatePageIsRedirectedNotLeftBlank() {
        page.navigate(EnvConfig.baseUrl() + "/profile/orders");
        page.waitForTimeout(3000);

        Assertions.assertFalse("about:blank".equals(page.url()),
                "ДЕФЕКТ: гость на /profile/orders остался на пустой странице about:blank — нет ни формы входа, ни редиректа");
    }

    @Test
    @Tag("AUTH-015")
    @P2
    @DisplayName("AUTH-015 · Оформление заказа гостем требует входа")
    void checkoutAsGuestRequiresSignIn() {
        HomePage home = new HomePage(page).open();
        home.firstProductCard().addToCart();

        CartPage cart = new CartPage(page).open();
        cart.checkoutButton().click();

        // Допустимы два варианта защиты: сразу форма входа или переход, не ведущий на страницу подтверждения.
        page.waitForTimeout(1500);
        var signInHeading = page.getByRole(AriaRole.HEADING,
                new com.microsoft.playwright.Page.GetByRoleOptions().setName(Text.SIGN_IN.get()));
        boolean askedToSignIn = signInHeading.isVisible();
        boolean reachedConfirmation = page.url().contains("/confirmation");
        Assertions.assertTrue(askedToSignIn || !reachedConfirmation,
                "Гость дошёл до страницы подтверждения заказа без входа: " + page.url());
    }
}
