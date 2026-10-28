package uz.aros.autotests.tests.storefront.orders;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.AuthenticatedUiTest;
import uz.aros.autotests.config.EnvConfig;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** «Мои заказы»: список, карточка заказа, детали, чужой заказ. Только чтение — заказы не создаются и не отменяются. */
@Epic("Storefront")
@Feature("Мои заказы")
@Tag("orders")
public class OrdersTest extends AuthenticatedUiTest {

    private void openOrders() {
        page.navigate(EnvConfig.baseUrl() + "/profile/orders");
        page.getByText("Активные").first().waitFor();
    }

    /** Первый ВИДИМЫЙ абзац с этим текстом (на странице бывают скрытые дубли — например, во всплывающих панелях). */
    private com.microsoft.playwright.Locator visibleText(String text) {
        return page.getByText(text).and(page.locator(":visible")).first();
    }

    /** Список заказов подгружается с сервера — ждём до 8 секунд, прежде чем решить, что заказов нет. */
    private boolean hasOrders(com.microsoft.playwright.Locator firstOrderNumber) {
        try {
            firstOrderNumber.waitFor(new com.microsoft.playwright.Locator.WaitForOptions().setTimeout(8_000));
            return true;
        } catch (com.microsoft.playwright.TimeoutError noOrders) {
            return false;
        }
    }

    @Test
    @Tag("ORD-001")
    @P1
    @Smoke
    @DisplayName("ORD-001 · Список заказов: вкладки «Активные» и «Все»")
    void ordersListHasTabs() {
        openOrders();

        assertThat(page.getByText("Активные").first()).isVisible();
        assertThat(page.getByText("Все", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).first()).isVisible();
    }

    @Test
    @Tag("ORD-002")
    @P2
    @DisplayName("ORD-002 · Карточка заказа содержит номер, дату, количество, оплату, доставку и сумму")
    void orderCardContainsMainFields() {
        openOrders();
        page.getByText("Все", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).first().click();
        var firstNumber = page.getByText(Pattern.compile("Номер заказа #\\d+")).first();
        Assumptions.assumeTrue(hasOrders(firstNumber), "У тестового пользователя нет заказов — проверять нечего");

        String visibleText = page.locator("body").innerText();
        for (String label : new String[]{"Число заказа", "Количество товара", "Способ оплаты", "Способ доставки", "Общая цена"}) {
            Assertions.assertTrue(visibleText.contains(label), "В карточке заказа не показано поле «" + label + "»");
        }
    }

    @Test
    @Tag("ORD-004")
    @P1
    @DisplayName("ORD-004 · Клик по заказу открывает его детали с тем же номером")
    void orderDetailsOpen() {
        openOrders();
        page.getByText("Все", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).first().click();
        var first = page.getByText(Pattern.compile("Номер заказа #\\d+")).first();
        Assumptions.assumeTrue(hasOrders(first), "У тестового пользователя нет заказов");
        String number = first.innerText().replaceAll("\\D", "");

        first.click();

        page.waitForURL(url -> url.contains("/profile/orders/" + number));
        assertThat(page.getByText("Номер заказа #" + number)).isVisible();
        assertThat(visibleText("Итого")).isVisible();
    }

    @Test
    @Tag("ORD-010")
    @P2
    @DisplayName("ORD-010 · Чужой/несуществующий заказ по прямой ссылке не показывает данных")
    void foreignOrderIsNotAccessible() {
        page.navigate(EnvConfig.baseUrl() + "/profile/orders/1");

        page.waitForTimeout(3000);
        String body = page.locator("body").innerText();
        Assertions.assertFalse(body.contains("Номер заказа #1\n") || body.contains("ТОВАРЫ"),
                "По ссылке на чужой заказ #1 показаны его данные");
    }
}
