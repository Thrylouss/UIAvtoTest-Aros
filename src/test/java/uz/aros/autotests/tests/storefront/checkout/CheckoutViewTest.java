package uz.aros.autotests.tests.storefront.checkout;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.AuthenticatedUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.CartPage;
import uz.aros.autotests.pages.storefront.CheckoutPage;
import uz.aros.autotests.pages.storefront.HomePage;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Страница оформления заказа — ТОЛЬКО просмотр и проверки. Ни один тест здесь не нажимает «Оформить заказ»,
 * поэтому заказы не создаются. (Тесты, создающие заказы, помечаются {@code @CreatesOrder} — см. README.)
 */
@Epic("Storefront")
@Feature("Оформление заказа")
@Tag("checkout")
public class CheckoutViewTest extends AuthenticatedUiTest {

    private CheckoutPage openCheckoutWithOneItem() {
        new HomePage(page).open().firstProductCard().addToCart();
        return new CartPage(page).open().proceedToCheckout();
    }

    @Test
    @Tag("CHK-001")
    @P1
    @Smoke
    @DisplayName("CHK-001 · Из корзины можно перейти к оформлению заказа")
    void cartLeadsToCheckout() {
        CheckoutPage checkout = openCheckoutWithOneItem();

        Assertions.assertTrue(page.url().contains("/confirmation"));
        assertThat(checkout.placeOrderButton()).isVisible();
    }

    @Test
    @Tag("CHK-002")
    @P1
    @DisplayName("CHK-002 · На оформлении показаны способ оплаты и способ доставки")
    void checkoutShowsPaymentAndDelivery() {
        openCheckoutWithOneItem();

        assertThat(page.getByText("Способ оплаты").first()).isVisible();
        assertThat(page.getByText("Способ доставки").first()).isVisible();
        assertThat(page.getByText("Наличные").first()).isVisible();
    }

    @Test
    @Tag("CHK-011")
    @P3
    @DisplayName("CHK-011 · Комментарий к заказу ограничен 500 символами")
    void commentIsLimitedTo500Characters() {
        CheckoutPage checkout = openCheckoutWithOneItem();

        checkout.commentField().fill("x".repeat(600));

        Assertions.assertTrue(checkout.commentField().inputValue().length() <= 500,
                "В комментарий поместилось больше 500 символов: " + checkout.commentField().inputValue().length());
        assertThat(page.getByText("500/500")).isVisible();
    }

    @Test
    @Tag("CHK-012")
    @P1
    @DisplayName("CHK-012 · Итоги оформления совпадают с корзиной и внутренне согласованы")
    void checkoutTotalsMatchCart() {
        new HomePage(page).open().firstProductCard().addToCart();
        CartPage cart = new CartPage(page).open();
        long cartTotal = cart.summaryTotal();
        long cartDiscount = cart.summaryDiscount();

        CheckoutPage checkout = cart.proceedToCheckout();
        checkout.total();

        Assertions.assertEquals(cartTotal, checkout.total() - checkout.deliveryPrice(),
                "Итого на оформлении (без доставки) не равно итогу корзины");
        Assertions.assertEquals(cartDiscount, checkout.discount(), "Скидка на оформлении не равна скидке в корзине");
        Assertions.assertEquals(checkout.goodsPrice() - checkout.discount() + checkout.deliveryPrice(), checkout.total(),
                "Цена − скидка + доставка ≠ итого на странице оформления");
    }

    @Test
    @Tag("CHK-013")
    @P3
    @DisplayName("CHK-013 · Ссылка на публичную оферту открывается")
    void publicOfferLinkWorks() {
        CheckoutPage checkout = openCheckoutWithOneItem();

        String href = checkout.publicOfferLink().getAttribute("href");
        var response = page.request().get(href.startsWith("http") ? href : EnvConfig.siteRoot() + href);

        Assertions.assertTrue(response.status() < 400, "Публичная оферта " + href + " отвечает " + response.status());
    }
}
