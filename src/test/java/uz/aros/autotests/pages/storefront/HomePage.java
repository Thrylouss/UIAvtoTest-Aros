package uz.aros.autotests.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.i18n.Text;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.components.CartIndicator;
import uz.aros.autotests.pages.storefront.components.ProductCard;
import uz.aros.autotests.pages.storefront.components.SearchBar;
import uz.aros.autotests.pages.storefront.components.SignInForm;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object главной страницы витрины (storefront).
 *
 * <p>Это "входная точка" для тестов: почти любой сценарий начинается с
 * {@code new HomePage(page).open()}.
 */
public class HomePage extends BasePage {

    /**
     * Локатор шапки сайта (тег {@code <header>}, роль доступности
     * {@code banner}). Кнопку "Sign in" ищем ИМЕННО внутри шапки, а не по
     * всей странице — потому что внутри открытой модалки логина тоже есть
     * кнопка с текстом "Sign in" (кнопка отправки формы), и без этого
     * ограничения локатор находил бы два элемента вместо одного.
     */
    private final Locator header;
    private final Locator signInButton;
    private final Locator productLinks;

    public HomePage(Page page) {
        super(page);
        this.header = page.getByRole(AriaRole.BANNER);
        this.signInButton = header.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(Text.SIGN_IN.get()).setExact(true));
        // Карточки товаров из блоков "New Products" / "Popular Products" —
        // и там, и там это ссылки на /product/..., отличить их друг от
        // друга нам не нужно: для динамических тестов годится ЛЮБОЙ товар,
        // который реально существует на сайте прямо сейчас.
        //
        // :visible обязателен. Всплывающее уведомление "Product added to
        // cart!" тоже содержит ссылку на товар (со своей картинкой), и
        // даже после того, как уведомление скрывается, эта ссылка какое-то
        // время остаётся в DOM (просто невидимая), а не удаляется. Без
        // фильтра по видимости она попадает в общий список ссылок и
        // сдвигает индексы — nth(1) после добавления первого товара в
        // корзину начинал указывать на ссылку ИЗ УВЕДОМЛЕНИЯ (тот же самый
        // товар, что уже добавили), а не на второй товар в сетке. Найдено
        // эмпирически, живым прогоном: assertion сравнивал второй товар
        // сам с собой.
        this.productLinks = page.locator("a[href*=\"/product/\"]:visible");
    }

    /** Открывает главную страницу окружения, заданного в конфигурации (BASE_URL). */
    @Step("Открыть главную страницу")
    public HomePage open() {
        page.navigate(EnvConfig.baseUrl());
        return this;
    }

    /** Открывает модальное окно входа и возвращает готовый к работе Page Object формы. */
    @Step("Открыть форму входа")
    public SignInForm openSignInForm() {
        signInButton.click();
        return new SignInForm(page).waitUntilVisible();
    }

    /**
     * Проверяет, что пользователь авторизован: кнопка "Sign in" в шапке
     * исчезла (после логина шапка показывает имя пользователя вместо неё).
     * Мы намеренно не проверяем конкретное имя — оно зависит от того, какой
     * тестовый пользователь используется, а факт исчезновения кнопки "Sign in"
     * универсален для любого пользователя.
     */
    @Step("Проверить, что пользователь авторизован")
    public void assertUserIsLoggedIn() {
        // Двойная проверка: кнопки «Вход» нет И появилась ссылка на профиль. Одной проверки «кнопки нет»
        // мало: пока страница не отрисовалась, кнопки тоже нет — тест прошёл бы «зря».
        assertThat(header.locator("a[href*=\"/profile\"]")).isVisible();
        assertThat(signInButton).hasCount(0);
    }

    /** Проверяет, что пользователь НЕ авторизован — кнопка "Sign in" всё ещё видна в шапке. */
    @Step("Проверить, что пользователь не авторизован")
    public void assertUserIsLoggedOut() {
        assertThat(signInButton).isVisible();
    }

    /** Строка поиска в шапке — доступна с главной страницы (как и с любой другой). */
    public SearchBar searchBar() {
        return new SearchBar(page);
    }

    /** Значок корзины в шапке — доступен с главной страницы (как и с любой другой). */
    public CartIndicator cartIndicator() {
        return new CartIndicator(page);
    }

    /** Все карточки товаров, которые сейчас показаны на главной странице (блоки "New"/"Popular"). */
    public List<ProductCard> productCards() {
        // Дожидаемся, пока сетка товаров реально отрисуется — без этого
        // на медленном рендере список мог оказаться короче, чем есть на
        // самом деле (например, всего 1 элемент вместо десятков), просто
        // потому что остальные карточки ещё не успели стать видимыми.
        productLinks.first().waitFor();
        return ProductCard.allFrom(page, productLinks);
    }

    /**
     * Первый попавшийся товар на главной странице — источник "реальных"
     * данных для динамических тестов поиска и каталога: вместо
     * захардкоженного названия товара тесты берут ЛЮБОЙ товар, который
     * прямо сейчас существует на сайте, и работают с ним.
     */
    public ProductCard firstProductCard() {
        productLinks.first().waitFor();
        return productCards().get(0);
    }
}
