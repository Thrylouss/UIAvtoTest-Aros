package uz.aros.autotests.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import org.junit.jupiter.api.Assertions;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.i18n.Text;

/**
 * Page Object страницы отдельного товара ({@code /product/<slug>?pvId=<id>}).
 *
 * <p>Пока страница описана минимально — ровно настолько, чтобы проверить,
 * что переход с карточки товара (из каталога/поиска) действительно привёл
 * на страницу ИМЕННО ЭТОГО товара. Более глубокое покрытие самой страницы
 * товара (выбор цвета/комплектации, добавление в корзину отсюда, отзывы) —
 * материал для отдельного, следующего модуля тестов.
 */
public class ProductPage extends BasePage {

    /** Главный заголовок страницы — название товара. Это первый heading в DOM, до любых заголовков блока отзывов. */
    private final Locator heading;

    public ProductPage(Page page) {
        super(page);
        this.heading = page.getByRole(AriaRole.HEADING).first();
    }

    /**
     * Проверяет, что открылся товар с ожидаемым pvId.
     *
     * <p>Сравниваем именно ID из URL, а не текст заголовка: заголовок на
     * странице товара переведён (например, на английский), а название на
     * карточке в каталоге — нет (см. подробности в {@link
     * uz.aros.autotests.pages.storefront.components.ProductCard}), так что
     * сравнение по тексту время от времени давало бы ложные срабатывания.
     * pvId в URL — язык-независимый и всегда один и тот же для одного и
     * того же товара.
     *
     * <p>Переход на эту страницу — это переход клиентского роутера (SPA),
     * а не полная перезагрузка: клик уже мог вернуть управление тесту, а
     * URL браузера ещё не успел обновиться. Без явного ожидания ниже
     * проверка иногда (не всегда — из-за этого и трудно было поймать)
     * читала СТАРЫЙ URL страницы каталога и ложно считала, что переход не
     * состоялся, хотя на самом деле он просто ещё не завершился. Тот же
     * класс гонки, что и при переходе на страницу поиска — см.
     * {@link uz.aros.autotests.pages.storefront.components.SearchBar#search}.
     */
    @Step("Проверить, что открылась страница товара с pvId={expectedPvId}")
    public void assertOpenedProductWithPvId(String expectedPvId) {
        page.waitForURL(url -> url.contains("pvId=" + expectedPvId) || url.contains("/product/"));
        heading.waitFor();
        String url = page.url();
        Assertions.assertTrue(url.contains("pvId=" + expectedPvId),
                "Ожидался переход на товар с pvId=" + expectedPvId + ", но открылся URL: " + url);
    }

    // ------------------------------------------------------------------
    // Элементы страницы товара (кейсы PDP-*)
    // ------------------------------------------------------------------

    /** Заголовок с названием и «(ID: …)» — самый первый заголовок в основной части страницы. */
    public Locator title() {
        return page.getByRole(AriaRole.HEADING).filter(new Locator.FilterOptions().setHasText("(ID:")).first();
    }

    public Locator breadcrumbs() {
        return page.locator("a[href*=\"/ru\"], a[href*=\"/en\"], a[href*=\"/uz\"]").filter(new Locator.FilterOptions().setHasText(Text.HOME_BREADCRUMB.get()));
    }

    public Locator quickBuyButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Text.QUICK_BUY.get()));
    }

    public Locator quantityInput() {
        return page.getByRole(AriaRole.SPINBUTTON).first();
    }

    /** Кнопки «−» и «+» вокруг счётчика (две иконочные кнопки рядом с полем количества). */
    public Locator decreaseButton() {
        return quantityInput().locator("xpath=ancestor::*[count(.//button)>=2][1]").locator("button").first();
    }

    public Locator increaseButton() {
        return quantityInput().locator("xpath=ancestor::*[count(.//button)>=2][1]").locator("button").nth(1);
    }

    @Step("Открыть товар по ссылке {href}")
    public ProductPage openByHref(String href) {
        page.navigate(href.startsWith("http") ? href : EnvConfig.siteRoot() + href);
        title().waitFor();
        return this;
    }

    /** Весь видимый текст страницы — для проверок «на странице есть …». */
    public String bodyText() {
        return page.locator("body").innerText();
    }
}
