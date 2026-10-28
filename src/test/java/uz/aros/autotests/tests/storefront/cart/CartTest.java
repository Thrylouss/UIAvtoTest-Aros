package uz.aros.autotests.tests.storefront.cart;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.pages.storefront.CartPage;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.CartLineItem;
import uz.aros.autotests.pages.storefront.components.ProductCard;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты корзины: добавление товара и проверка того, что он там реально
 * оказался (и ведёт себя правильно при изменении количества/удалении).
 *
 * <p>Как и {@code SearchTest}, эти тесты ничего не хардкодят — товар для
 * добавления в корзину каждый раз берётся динамически через
 * {@link HomePage#firstProductCard()} / {@link HomePage#productCards()}
 * (см. подробное объяснение подхода в {@code SearchTest}). Корзина здесь
 * — гостевая (без авторизации): добавление в корзину на этом сайте не
 * требует входа, что само по себе удобно проверить один раз и
 * пользоваться дальше.
 */
@Epic("Storefront")
@Feature("Корзина")
@Tag("cart")
public class CartTest extends BaseUiTest {

    @Test
    @Tag("CART-001")
    @P1
    @Smoke
    @DisplayName("CART-001 · После добавления товара карточка показывает степпер количества, а бейдж корзины — 1")
    void addingProductShowsStepperOnCardAndUpdatesCartBadge() {
        HomePage homePage = new HomePage(page).open();
        ProductCard product = homePage.firstProductCard();

        product.addToCart();

        assertTrue(product.isInCart(), "После клика по 'Добавить в корзину' карточка должна перейти в режим степпера");
        homePage.cartIndicator().assertDistinctItemsCount(1);
    }

    @Test
    @Tag("CART-002")
    @P1
    @Smoke
    @DisplayName("CART-002 · Добавленный товар появляется на странице корзины с правильным количеством")
    void addedProductAppearsInCartPageWithCorrectQuantity() {
        HomePage homePage = new HomePage(page).open();
        ProductCard product = homePage.firstProductCard();
        String pvId = product.pvId();

        product.addToCart();
        CartPage cart = homePage.cartIndicator().open();

        cart.findByPvId(pvId).assertQuantity(1);
        cart.assertItemsCount(1);
    }

    @Test
    @Tag("CART-003")
    @P1
    @DisplayName("CART-003 · Увеличение количества в корзине обновляет счётчик товаров")
    void increasingQuantityInCartUpdatesItemsCount() {
        HomePage homePage = new HomePage(page).open();
        ProductCard product = homePage.firstProductCard();
        String pvId = product.pvId();
        product.addToCart();

        CartPage cart = homePage.cartIndicator().open();
        CartLineItem lineItem = cart.findByPvId(pvId);
        lineItem.increaseQuantity();
        lineItem.increaseQuantity();

        lineItem.assertQuantity(3);
        cart.assertItemsCount(3);
    }

    @Test
    @Tag("CART-004")
    @P2
    @DisplayName("CART-004 · Уменьшение количества до нуля удаляет товар и опустошает корзину")
    void decreasingQuantityToZeroRemovesItemAndEmptiesCart() {
        HomePage homePage = new HomePage(page).open();
        ProductCard product = homePage.firstProductCard();
        String pvId = product.pvId();
        product.addToCart();

        CartPage cart = homePage.cartIndicator().open();
        cart.findByPvId(pvId).decreaseQuantity();

        cart.assertEmpty();
    }

    @Test
    @Tag("CART-005")
    @P2
    @DisplayName("CART-005 · Удаление одного товара оставляет остальные в корзине нетронутыми")
    void removingOneItemKeepsOthersInCart() {
        HomePage homePage = new HomePage(page).open();
        List<ProductCard> products = homePage.productCards();
        ProductCard firstProduct = products.get(0);
        ProductCard secondProduct = products.get(1);
        String firstPvId = firstProduct.pvId();
        String secondPvId = secondProduct.pvId();

        firstProduct.addToCart();
        secondProduct.addToCart();
        CartPage cart = homePage.cartIndicator().open();
        cart.assertItemsCount(2);

        cart.removeItem(firstPvId);

        cart.assertItemsCount(1);
        List<String> remaining = cart.allPvIds();
        assertTrue(remaining.contains(secondPvId), "Второй товар должен остаться в корзине");
        assertFalse(remaining.contains(firstPvId), "Первый товар должен был удалиться из корзины");
    }
}
