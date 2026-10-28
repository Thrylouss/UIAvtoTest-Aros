package uz.aros.autotests.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.i18n.Text;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.components.CartLineItem;

import java.util.ArrayList;
import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object страницы корзины ({@code /cart}).
 *
 * <p>Каждая строка корзины устроена так, что у чекбокса выбора атрибут
 * {@code value} — это pvId товара (например, {@code value="9179"}), а у
 * чекбокса "Выбрать всё" — {@code value="on"}. Это удобная, стабильная
 * зацепка: по ней и строится {@link #findByPvId}, и по ней же находится
 * реальный список товаров в {@link #allPvIds()} (просто все чекбоксы
 * кроме "on").
 */
public class CartPage extends BasePage {

    private final Locator emptyStateMessage;
    private final Locator anyItemCheckbox;
    private final Locator itemsCountValue;
    private final Locator totalValue;
    private final Locator deleteSelectedButton;

    public CartPage(Page page) {
        super(page);
        // :visible — на странице есть невидимый дубль этой надписи (иначе .first() мог бы указать на него).
        this.emptyStateMessage = page.getByText(Text.CART_EMPTY.get()).and(page.locator(":visible")).first();
        // Чекбоксы строк — единственные с числовым value (pvId); у
        // чекбокса "Выбрать всё" value="on", поэтому его явно исключаем.
        this.anyItemCheckbox = page.locator("input[type=\"checkbox\"]:not([value=\"on\"])");
        this.itemsCountValue = summaryValue(Text.CART_ITEMS_COUNT);
        this.totalValue = summaryValue(Text.CART_TOTAL);
        // "Delete" — НЕ настоящая кнопка: это <p>Delete</p> внутри <div>
        // с обработчиком клика, без role="button" и без тега <button>.
        // getByRole(BUTTON, "Delete") никогда её не находил — было
        // обнаружено эмпирически, живым прогоном (тест стабильно падал по
        // таймауту на этом самом клике). Ищем по тексту напрямую.
        this.deleteSelectedButton = page.getByText(Text.CART_DELETE.get(), new Page.GetByTextOptions().setExact(true));
    }

    /** Открывает страницу корзины напрямую (без перехода по иконке в шапке). */
    @Step("Открыть корзину")
    public CartPage open() {
        page.navigate(EnvConfig.baseUrl() + "/cart");
        return waitUntilLoaded();
    }

    /** Дожидается, пока страница определится: есть товары или корзина пуста. Вызывать сразу после открытия/перехода. */
    @Step("Дождаться загрузки корзины")
    public CartPage waitUntilLoaded() {
        anyItemCheckbox.first().or(emptyStateMessage).waitFor();
        return this;
    }

    @Step("Проверить, что корзина пуста")
    public void assertEmpty() {
        assertThat(emptyStateMessage).isVisible();
    }

    /**
     * Проверяет число "Products count" в сводке корзины.
     *
     * <p>Два важных нюанса, оба найдены эмпирически, живым прогоном:
     * <ul>
     *   <li>это СУММА количеств строк, а не число различных товарных
     *       позиций (одна позиция с количеством 3 показывает "3 Items");</li>
     *   <li>считаются только ОТМЕЧЕННЫЕ чекбоксом строки — сводка следует
     *       за выбором, а не за составом корзины целиком. По умолчанию
     *       отмечено всё, поэтому в обычных сценариях разницы не видно —
     *       но именно поэтому {@link #removeItem} в конце заново отмечает
     *       всё оставшееся: без этого следующая проверка через этот метод
     *       ловила бы "0 Items" на полностью реальных оставшихся товарах.</li>
     * </ul>
     */
    @Step("Проверить общее количество товаров в корзине: {expectedTotalQuantity}")
    public void assertItemsCount(int expectedTotalQuantity) {
        assertThat(itemsCountValue).containsText(expectedTotalQuantity + " " + Text.CART_ITEMS_UNIT.get());
    }

    /** Все pvId товаров, сейчас лежащих в корзине. */
    public List<String> allPvIds() {
        List<String> pvIds = new ArrayList<>();
        int count = anyItemCheckbox.count();
        for (int i = 0; i < count; i++) {
            String value = anyItemCheckbox.nth(i).getAttribute("value");
            if (value != null) {
                pvIds.add(value);
            }
        }
        return pvIds;
    }

    /** Строка конкретного товара по его pvId — используйте вместе с {@link uz.aros.autotests.pages.storefront.components.ProductCard#pvId()}. */
    public CartLineItem findByPvId(String pvId) {
        return new CartLineItem(page, pvId);
    }

    /**
     * Удаляет из корзины ровно один товар, не трогая остальные.
     *
     * <p>На странице нет отдельной "мусорной корзины" на каждую строку,
     * которую было бы просто и надёжно выделить локатором (см. подробности
     * в истории разработки — там несколько похожих на вид кнопок, и не всё
     * то, что выглядит как кнопка удаления строки, ей является). Вместо
     * этого используется гарантированно рабочий путь тем же способом, что
     * доступен и живому пользователю: снять галочки со всех ОСТАЛЬНЫХ
     * строк, оставить галочку только на нужной, нажать общую кнопку
     * "Delete" — она удаляет ровно то, что отмечено чекбоксами.
     *
     * <p>После удаления заново отмечаем всё, что осталось. Без этого шага
     * оставшиеся товары остаются НЕотмеченными (мы же их explicitly
     * сняли), и сводка "Products count"/"Total" в правой панели продолжает
     * считать только отмеченное — то есть покажет "0 Items", хотя в
     * корзине реально что-то есть. Это сбивало с толку при первой версии
     * этого метода: сам товар удалялся правильно, но проверка сводки
     * после этого ложно проваливалась.
     */
    @Step("Удалить товар с pvId={pvId} из корзины")
    public void removeItem(String pvId) {
        for (String otherPvId : allPvIds()) {
            findByPvId(otherPvId).setSelected(otherPvId.equals(pvId));
        }
        // Та же причина, что и у паузы в CartLineItem.increaseQuantity():
        // серия быстрых действий подряд (несколько чекбоксов) иногда
        // приводит к toastify-тосту с ошибкой, чей фиксированный контейнер
        // перекрывает кнопку "Delete". Даём странице немного времени
        // устояться перед финальным кликом.
        page.waitForTimeout(700);
        deleteSelectedButton.click();

        // Удаление тоже не мгновенно — дожидаемся, пока строка удаляемого
        // товара реально пропадёт из DOM, прежде чем читать, что осталось,
        // и заново отмечать оставшееся.
        page.locator("input[type=\"checkbox\"][value=\"" + pvId + "\"]")
                .waitFor(new Locator.WaitForOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.DETACHED));

        for (String remainingPvId : allPvIds()) {
            findByPvId(remainingPvId).setSelected(true);
        }
    }

    // ------------------------------------------------------------------
    // Итоги корзины и массовые действия (кейсы CART-006..013)
    // ------------------------------------------------------------------

    /**
     * Значение из блока итогов по подписи. Подпись («Цена товаров») и значение («160 000 сум») — соседние {@code <p>}.
     *
     * <p>ВАЖНО — {@code :visible}. В DOM страницы корзины ДВА блока итогов, и один из них скрыт (его «Итого:»
     * не пересчитывается при снятии отметок и всегда показывает старую сумму). Без фильтра по видимости
     * тест читал бы устаревшее значение скрытого дубля.
     */
    private Locator summaryValue(Text label) {
        return page.locator("p:visible")
                .filter(new Locator.FilterOptions().setHasText(uz.aros.autotests.base.Regex.exact(label.get())))
                .locator("xpath=following-sibling::p[1]").first();
    }

    /** «Цена товаров» из блока итогов, в сумах (только цифры). */
    public long summaryGoodsPrice() {
        return digits(summaryValue(Text.CART_GOODS_PRICE).innerText());
    }

    /** «Скидка» из блока итогов, по модулю (на сайте показано как «-5 000 сум»). */
    public long summaryDiscount() {
        return digits(summaryValue(Text.CART_DISCOUNT).innerText());
    }

    /** «Итого» из блока итогов. */
    public long summaryTotal() {
        return digits(totalValue.innerText());
    }

    /** Число товаров в блоке «Количество товаров» («3 шт» → 3). */
    public int summaryItemsCount() {
        return (int) digits(itemsCountValue.innerText());
    }

    /** Достаёт из строки вроде «-5 000 сум» число 5000. */
    public static long digits(String text) {
        String d = text.replaceAll("[^0-9]", "");
        return d.isEmpty() ? 0 : Long.parseLong(d);
    }

    /** Чекбокс «Выбрать все» (не относится к конкретному товару — у него нет value с pvId). */
    public Locator selectAllCheckbox() {
        return page.getByText(Text.CART_SELECT_ALL.get(), new Page.GetByTextOptions().setExact(true))
                .locator("xpath=preceding::input[@type='checkbox'][1]");
    }

    public Locator checkoutButton() {
        return page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Text.CART_CHECKOUT.get()).setExact(true));
    }

    /** Нажимает «Оформить» → в окне «Срок поставки» жмёт «Продолжать» → оказываемся на странице подтверждения заказа. */
    @Step("Нажать «Оформить» и подтвердить срок поставки")
    public CheckoutPage proceedToCheckout() {
        checkoutButton().click();
        // Окно «Срок поставки → Продолжать» показывается не всегда (только если у товара есть срок поставки),
        // поэтому ждём ЛИБО кнопку «Продолжать», ЛИБО сразу переход на оформление.
        Locator continueButton = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Text.CART_CONTINUE.get()));
        long deadline = System.currentTimeMillis() + 10_000;
        while (!page.url().contains("/confirmation") && System.currentTimeMillis() < deadline) {
            if (continueButton.isVisible()) {
                continueButton.click();
                break;
            }
            page.waitForTimeout(200);
        }
        page.waitForURL(url -> url.contains("/confirmation"));
        return new CheckoutPage(page).waitUntilLoaded();
    }

    /** Нажимает «Удалить» для выбранных строк (кнопка на сайте — обычный абзац текста). */
    @Step("Удалить выбранные товары")
    public void deleteSelected() {
        deleteSelectedButton.click();
    }

    /**
     * Полностью очищает корзину: отмечает все строки и нажимает «Удалить».
     * Используется в {@code @AfterEach}, чтобы тесты не влияли друг на друга.
     */
    @Step("Очистить корзину")
    public void clear() {
        waitUntilLoaded();
        if (allPvIds().isEmpty()) {
            return;
        }
        for (String pvId : allPvIds()) {
            findByPvId(pvId).setSelected(true);
        }
        page.waitForTimeout(500);
        deleteSelectedButton.click();
        emptyStateMessage.waitFor();
    }
}
