package uz.aros.autotests.pages.storefront.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import uz.aros.autotests.pages.storefront.CartPage;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Значок корзины в шапке сайта — доступен с любой страницы витрины, как
 * и {@link SearchBar}.
 *
 * <p>Число на значке — это MUI-компонент {@code Badge}, который рисует
 * число в {@code <span class="MuiBadge-badge">}. Когда корзина пуста,
 * этого span'а вообще нет в DOM (а не "0" внутри него) — так и
 * определяем пустоту в {@link #assertEmpty()}.
 *
 * <p><b>Важно:</b> это число — количество РАЗНЫХ товарных позиций в
 * корзине, а НЕ сумма количеств (в отличие от "Products count" на самой
 * странице корзины, см. {@link CartPage#assertItemsCount}). Проверено
 * эмпирически: после увеличения количества одного и того же товара с 1
 * до 3 бейдж в шапке остался "1", хотя на странице корзины количество
 * показывало "3 Items". Два разных числа с виду про одно и то же —
 * типичная ловушка для теста, если не свериться с реальным поведением
 * сайта заранее.
 */
public class CartIndicator {

    private final Page page;
    private final Locator cartLink;
    private final Locator badge;

    public CartIndicator(Page page) {
        this.page = page;
        // ВАЖНО: искать ссылку на корзину только по href="/cart" внутри
        // шапки недостаточно — попап "Product added to cart!" (см.
        // ProductCard.addToCart()) тоже содержит ссылку на /cart (кнопка
        // "Go to cart") и на практике оказывается в том же landmark
        // banner, из-за чего локатор находил ОБА элемента сразу. Значок
        // же в шапке — иконка без подписи (MUI-класс MuiIconButton-root),
        // а "Go to cart" в попапе — залитая кнопка с текстом
        // (MuiButton-contained), так что различаем их по этому классу.
        this.cartLink = page.getByRole(AriaRole.BANNER).locator("a.MuiIconButton-root[href*=\"/cart\"]");
        this.badge = cartLink.locator(".MuiBadge-badge");
    }

    /** Количество различных товарных позиций в корзине (0, если бейдж вообще не отображается). */
    public int distinctItemsCount() {
        if (badge.count() == 0) {
            return 0;
        }
        return Integer.parseInt(badge.innerText());
    }

    @Step("Проверить, что бейдж корзины показывает {expected}")
    public void assertDistinctItemsCount(int expected) {
        if (expected == 0) {
            assertThat(badge).hasCount(0);
        } else {
            assertThat(badge).hasText(String.valueOf(expected));
        }
    }

    /** Переходит в корзину по значку в шапке. */
    @Step("Перейти в корзину")
    public CartPage open() {
        cartLink.click();
        return new CartPage(page).waitUntilLoaded();
    }
}
