package uz.aros.autotests.tests.storefront.product;

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
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.ProductPage;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Страница товара: основные элементы, счётчик количества, блок «Ещё товары», несуществующий товар. */
@Epic("Storefront")
@Feature("Карточка товара")
@Tag("product")
public class ProductPageTest extends BaseUiTest {

    private ProductPage openFirstHomeProduct() {
        new HomePage(page).open().firstProductCard().open();
        page.waitForURL(url -> url.contains("/product/"));
        ProductPage product = new ProductPage(page);
        product.title().waitFor();
        return product;
    }

    @Test
    @Tag("PDP-001")
    @P1
    @Smoke
    @DisplayName("PDP-001 · Страница товара показывает название с ID, цену, «Быструю покупку», остатки")
    void productPageShowsMainElements() {
        ProductPage product = openFirstHomeProduct();

        assertThat(product.title()).containsText("(ID:");
        assertThat(page.getByText(Pattern.compile("\\d[\\d ]*\\s(сум|so.m)")).first()).isVisible();
        assertThat(product.quickBuyButton()).isVisible();
        assertThat(product.quantityInput()).isVisible();
    }

    @Test
    @Tag("PDP-004")
    @P1
    @DisplayName("PDP-004 · Кнопки «+» и «−» на странице товара меняют количество")
    void quantityStepperChangesValue() {
        ProductPage product = openFirstHomeProduct();
        assertThat(product.quantityInput()).hasValue("0");

        product.increaseButton().click();
        assertThat(product.quantityInput()).hasValue("1");

        product.decreaseButton().click();
        assertThat(product.quantityInput()).hasValue("0");
    }

    @Test
    @Tag("PDP-010")
    @P3
    @DisplayName("PDP-010 · Блок «Ещё товары для <модель>» показывает количество и карточки")
    void moreProductsForModelBlock() {
        // Блок есть не у всех товаров (только у тех, что привязаны к модели устройства), поэтому берём
        // товар из выдачи по ЭКРАНАМ — они всегда привязаны к модели.
        new uz.aros.autotests.pages.storefront.CatalogPage(page).openWithQuery("ekran").productCards().get(0).open();
        page.waitForURL(url -> url.contains("/product/"));
        new ProductPage(page).title().waitFor();

        assertThat(page.getByText(Pattern.compile("Ещё товары для .+\\(\\d+\\)")).first()).isVisible();
    }

    @Test
    @Tag("PDP-012")
    @P3
    @DisplayName("PDP-012 · Несуществующий товар по прямой ссылке — страница «не найдено», а не белый экран")
    void unknownProductShowsNotFound() {
        page.navigate(EnvConfig.baseUrl() + "/product/no-such-product-0?pvId=999999999");

        page.waitForTimeout(2500);
        String body = page.locator("body").innerText();
        Assertions.assertFalse(body.isBlank(), "Страница несуществующего товара пустая");
        Assertions.assertFalse(Pattern.compile("(?i)exception|stack ?trace|undefined|NaN").matcher(body).find(),
                "На странице несуществующего товара видны технические тексты");
        assertThat(page.locator("a[href=\"" + java.net.URI.create(EnvConfig.baseUrl()).getPath() + "\"], a[href=\"/\"]").first()).isVisible();
    }
}
