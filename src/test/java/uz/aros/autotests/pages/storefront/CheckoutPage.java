package uz.aros.autotests.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.i18n.Text;

/**
 * Страница подтверждения (оформления) заказа — маршрут {@code /confirmation}.
 *
 * <p>ВАЖНО: открытие страницы заказ НЕ создаёт. Заказ создаётся только кнопкой «Оформить заказ»;
 * в этом классе её нажатие вынесено в отдельный метод {@link #placeOrder()} и используется только в тестах
 * с тегом {@code creates-order}.
 */
public class CheckoutPage extends BasePage {

    public CheckoutPage(Page page) {
        super(page);
    }

    @Step("Дождаться загрузки страницы подтверждения заказа")
    public CheckoutPage waitUntilLoaded() {
        placeOrderButton().waitFor();
        return this;
    }

    public Locator placeOrderButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Text.PLACE_ORDER.get()).setExact(true));
    }

    /**
     * Значение из блока итогов: берём видимый текст страницы и читаем строку, идущую сразу за подписью
     * («Цена продукции:» → «160 000 сум»). Так не зависим от того, какими тегами сверстан блок, и от скрытых
     * дублей. Если строки нет (например, нет скидки) — возвращаем 0.
     */
    private long valueAfter(String label) {
        String[] lines = page.locator("body").innerText().split("\\R");
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().equals(label)) {
                for (int j = i + 1; j < lines.length; j++) {
                    if (!lines[j].isBlank()) {
                        return CartPage.digits(lines[j]);
                    }
                }
            }
        }
        return 0;
    }

    public long goodsPrice() {
        return valueAfter("Цена продукции:");
    }

    public long discount() {
        return valueAfter(Text.CART_DISCOUNT.get() + ":");
    }

    public long deliveryPrice() {
        return valueAfter("Стоимость доставки:");
    }

    /** «Итого» — значения блока итогов приходят отдельным запросом после отрисовки страницы, поэтому ждём до 10 с. */
    public long total() {
        long deadline = System.currentTimeMillis() + 10_000;
        long value = valueAfter(Text.CART_TOTAL.get());
        while (value == 0 && System.currentTimeMillis() < deadline) {
            page.waitForTimeout(300);
            value = valueAfter(Text.CART_TOTAL.get());
        }
        return value;
    }

    public Locator commentField() {
        return page.locator("textarea").first();
    }

    public Locator publicOfferLink() {
        return page.locator("a[href*=\"oferta\"]").last();
    }

    /** Нажимает «Оформить заказ». ВНИМАНИЕ: создаёт реальный заказ на staging. */
    @Step("Нажать «Оформить заказ» (СОЗДАЁТ ЗАКАЗ)")
    public void placeOrder() {
        placeOrderButton().click();
    }
}
