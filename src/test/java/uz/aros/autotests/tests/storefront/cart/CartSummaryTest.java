package uz.aros.autotests.tests.storefront.cart;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.base.AuthenticatedUiTest;
import uz.aros.autotests.pages.storefront.CartPage;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.CartLineItem;
import uz.aros.autotests.pages.storefront.components.ProductCard;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Корзина вошедшего пользователя: итоги, выбор строк, лимит количества, сохранение между сессиями.
 * Товары добавляются с главной; после каждого теста корзина очищается ({@link AuthenticatedUiTest}).
 */
@Epic("Storefront")
@Feature("Корзина")
@Tag("cart")
public class CartSummaryTest extends AuthenticatedUiTest {

    private CartPage addTwoDifferentProductsAndOpenCart() {
        HomePage home = new HomePage(page).open();
        List<ProductCard> cards = home.productCards();
        cards.get(0).addToCart();
        cards.get(1).addToCart();
        return new CartPage(page).open();
    }

    @Test
    @Tag("CART-006")
    @P1
    @DisplayName("CART-006 · Итоги корзины согласованы: цена − скидка = итого, и растут при увеличении количества")
    void cartTotalsAreConsistent() {
        new HomePage(page).open().firstProductCard().addToCart();
        CartPage cart = new CartPage(page).open();

        long goods = cart.summaryGoodsPrice();
        long discount = cart.summaryDiscount();
        long total = cart.summaryTotal();
        Assertions.assertEquals(goods - discount, total,
                "Цена товаров " + goods + " − скидка " + discount + " ≠ итого " + total);

        CartLineItem line = cart.findByPvId(cart.allPvIds().get(0));
        line.increaseQuantity();
        line.assertQuantity(2);
        page.waitForTimeout(800);

        Assertions.assertEquals(goods * 2, cart.summaryGoodsPrice(), "«Цена товаров» не удвоилась при количестве 2");
        Assertions.assertEquals(total * 2, cart.summaryTotal(), "«Итого» не удвоилось при количестве 2");
        cart.assertItemsCount(2);
    }

    @Test
    @Tag("CART-007")
    @P1
    @DisplayName("CART-007 · Снятие отметки с товара пересчитывает итог и количество")
    void uncheckingItemRecalculatesTotal() {
        CartPage cart = addTwoDifferentProductsAndOpenCart();
        List<String> ids = cart.allPvIds();
        Assertions.assertEquals(2, ids.size(), "В корзине ожидалось 2 разных товара");
        long totalBoth = cart.summaryTotal();

        cart.findByPvId(ids.get(1)).setSelected(false);

        // Итоги пересчитываются запросом к серверу — ждём до 6 секунд, пока число изменится.
        long deadline = System.currentTimeMillis() + 6_000;
        while (cart.summaryTotal() >= totalBoth && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(300);
        }
        Assertions.assertTrue(cart.summaryTotal() < totalBoth,
                "После снятия отметки итог не уменьшился: " + totalBoth + " → " + cart.summaryTotal());
        cart.assertItemsCount(1);
    }

    @Test
    @Tag("CART-008")
    @P2
    @DisplayName("CART-008 · «Выбрать все» снимает и ставит отметки у всех товаров")
    void selectAllTogglesEveryRow() {
        CartPage cart = addTwoDifferentProductsAndOpenCart();
        List<String> ids = cart.allPvIds();

        cart.selectAllCheckbox().uncheck();
        page.waitForTimeout(600);
        for (String id : ids) {
            assertThat(page.locator("input[type=\"checkbox\"][value=\"" + id + "\"]")).not().isChecked();
        }

        cart.selectAllCheckbox().check();
        page.waitForTimeout(600);
        for (String id : ids) {
            assertThat(page.locator("input[type=\"checkbox\"][value=\"" + id + "\"]")).isChecked();
        }
    }

    @Test
    @Tag("CART-010")
    @P1
    @DisplayName("CART-010 · Ручной ввод количества больше остатка не принимается")
    void manualQuantityAboveStockIsRejected() {
        new HomePage(page).open().firstProductCard().addToCart();
        CartPage cart = new CartPage(page).open();
        CartLineItem line = cart.findByPvId(cart.allPvIds().get(0));

        line.typeQuantity(999_999);
        page.waitForTimeout(1500);

        Assertions.assertTrue(line.quantity() < 999_999,
                "Сайт принял невозможное количество 999999 (остаток на складе конечен)");
    }

    @Test
    @Tag("CART-011")
    @P1
    @DisplayName("CART-011 · Корзина сохраняется после перезагрузки и в новой вкладке")
    void cartPersistsAcrossReloadAndTabs() {
        new HomePage(page).open().firstProductCard().addToCart();
        CartPage cart = new CartPage(page).open();
        String pvId = cart.allPvIds().get(0);

        page.reload();
        cart = new CartPage(page).waitUntilLoaded();
        Assertions.assertTrue(cart.allPvIds().contains(pvId), "После перезагрузки товар пропал из корзины");

        com.microsoft.playwright.Page secondTab = page.context().newPage();
        CartPage cartInTab = new CartPage(secondTab).open();
        Assertions.assertTrue(cartInTab.allPvIds().contains(pvId), "В новой вкладке корзина пустая");
        secondTab.close();
    }

    @Test
    @Tag("CART-012")
    @P3
    @DisplayName("CART-012 · Строка товара: ссылка на товар, количество, цена")
    void cartRowShowsMainElements() {
        new HomePage(page).open().firstProductCard().addToCart();
        CartPage cart = new CartPage(page).open();

        CartLineItem line = cart.findByPvId(cart.allPvIds().get(0));

        assertThat(line.row().locator("a[href*=\"/product/\"]").first()).isVisible();
        assertThat(line.row().getByRole(com.microsoft.playwright.options.AriaRole.SPINBUTTON)).isVisible();
        assertThat(line.row().getByText(java.util.regex.Pattern.compile("сум|so.m")).first()).isVisible();
    }

    @Test
    @Tag("CART-013")
    @P2
    @DisplayName("CART-013 · «Оформить» без выбранных товаров не ведёт к оформлению")
    void checkoutWithoutSelectedItems() {
        new HomePage(page).open().firstProductCard().addToCart();
        CartPage cart = new CartPage(page).open();

        cart.selectAllCheckbox().uncheck();
        page.waitForTimeout(800);

        boolean disabled = cart.checkoutButton().isDisabled();
        if (!disabled) {
            cart.checkoutButton().click();
            page.waitForTimeout(1500);
        }
        Assertions.assertFalse(page.url().contains("/confirmation"), "Оформление открылось при пустом выборе товаров");
    }
}
