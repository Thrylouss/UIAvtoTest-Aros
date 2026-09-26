package uz.aros.autotests.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.components.SignInForm;

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

    public HomePage(Page page) {
        super(page);
        this.header = page.getByRole(AriaRole.BANNER);
        this.signInButton = header.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Sign in").setExact(true));
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
        assertThat(signInButton).hasCount(0);
    }

    /** Проверяет, что пользователь НЕ авторизован — кнопка "Sign in" всё ещё видна в шапке. */
    @Step("Проверить, что пользователь не авторизован")
    public void assertUserIsLoggedOut() {
        assertThat(signInButton).isVisible();
    }
}
