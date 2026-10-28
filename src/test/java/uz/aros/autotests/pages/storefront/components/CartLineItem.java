package uz.aros.autotests.pages.storefront.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Одна строка товара на странице корзины ({@code /cart}).
 *
 * <h2>Почему локаторы ищут именно так</h2>
 * Чекбокс строки — удобная зацепка: его {@code value} — это pvId товара,
 * и он единственный в своём роде. Поэтому строку ищем именно от чекбокса
 * вверх (как и в {@link ProductCard}, где то же самое решается поиском
 * вверх до ближайшего предка с нужным содержимым).
 *
 * <p>Поле количества ({@code input[type="number"]}) — как и на карточке
 * товара (см. {@link ProductCard#quantityInput()}) — реально хранит
 * значение, но имеет нулевой размер: сайт рисует видимое число как-то
 * иначе, этот input существует только для логики. Поэтому его значение
 * читаем обычным {@code inputValue()}/{@code hasValue()} (это работает и
 * на невидимых элементах), но НЕ ждём его видимости и не кликаем по нему.
 * А вот кнопки "-"/"+" — самые обычные, видимые, кликабельные элементы.
 */
public class CartLineItem {

    private final Page page;
    private final String pvId;
    private final Locator checkbox;
    private final Locator row;

    public CartLineItem(Page page, String pvId) {
        this.page = page;
        this.pvId = pvId;
        this.checkbox = page.locator("input[type=\"checkbox\"][value=\"" + pvId + "\"]");
        this.row = checkbox.locator("xpath=ancestor::div[.//input[@type='number']][1]");
    }

    public String pvId() {
        return pvId;
    }

    private Locator quantityInput() {
        return row.locator("input[type=\"number\"]").first();
    }

    private Locator decreaseButton() {
        return row.locator("button.MuiButton-text:visible").first();
    }

    private Locator increaseButton() {
        return row.locator("button.MuiButton-text:visible").last();
    }

    /** Текущее количество этого товара в корзине. */
    public int quantity() {
        return Integer.parseInt(quantityInput().inputValue());
    }

    @Step("Увеличить количество в корзине")
    public void increaseQuantity() {
        increaseButton().click();
        settleAfterQuantityChange();
    }

    /** Уменьшает количество; если оно было равно 1 — товар полностью удаляется из корзины (поведение сайта). */
    @Step("Уменьшить количество в корзине")
    public void decreaseQuantity() {
        decreaseButton().click();
        settleAfterQuantityChange();
    }

    /**
     * Небольшая пауза после клика по +/-.
     *
     * <p>Обнаружено эмпирически, живым прогоном: если менять количество
     * подряд, БЕЗ паузы между кликами (например, дважды нажать "+" одно
     * за другим), сайт иногда отвечает на второй запрос toastify-тостом с
     * ошибкой (похоже на защиту от слишком частых запросов). Сам тост —
     * не проблема, но его контейнер зафиксирован в углу экрана и
     * перекрывает часть шапки, из-за чего последующие клики (например, по
     * значку корзины) переставали проходить. Простая пауза между
     * изменениями количества устраняет саму причину, а не только симптом.
     */
    private void settleAfterQuantityChange() {
        page.waitForTimeout(700);
    }

    /**
     * Ждёт, пока количество на экране станет равно ожидаемому.
     *
     * <p>Изменение количества шлёт запрос на сервер не мгновенно (виден
     * небольшой техническими debounce), поэтому обычное сравнение чисел
     * сразу после клика по +/- может поймать старое значение. У Playwright
     * есть готовая retrying-проверка именно для значения инпута —
     * {@code assertThat(locator).hasValue(...)} — она сама повторяет
     * попытки, пока значение не совпадёт (или не кончится таймаут).
     */
    @Step("Проверить количество в корзине: {expected}")
    public void assertQuantity(int expected) {
        assertThat(quantityInput()).hasValue(String.valueOf(expected));
    }

    /** Отмечает / снимает отметку с чекбокса строки — используется перед массовым удалением, см. {@link uz.aros.autotests.pages.storefront.CartPage#removeItem}. */
    public void setSelected(boolean selected) {
        if (selected) {
            checkbox.check();
        } else {
            checkbox.uncheck();
        }
    }

    /** Строка корзины целиком (нужна тестам для проверки состава строки). */
    public Locator row() {
        return row;
    }

    /**
     * Вводит количество вручную. Поле числа в MUI имеет нулевой размер, поэтому обычный клик по нему невозможен;
     * {@code fill} + потеря фокуса (Tab) работают без клика и запускают тот же обработчик, что и ввод с клавиатуры.
     */
    @Step("Ввести количество вручную: {quantity}")
    public void typeQuantity(int quantity) {
        Locator input = quantityInput();
        input.fill(String.valueOf(quantity));
        input.press("Tab");
    }
}
