package uz.aros.autotests.pages.admin;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.config.EnvConfig;

/**
 * Page Object страницы логина админ-панели (staging-admin.aros.uz).
 *
 * <p>Это отдельное приложение (другой домен, другая вёрстка), поэтому у
 * него свой пакет {@code pages.admin}, независимый от {@code pages.storefront}.
 * Общее у них — оба наследуются от {@link BasePage} и используют один и
 * тот же {@link uz.aros.autotests.playwrightsupport.PlaywrightManager}.
 *
 * <p>В отличие от витрины (вход по телефону), тут обычная форма
 * "логин + пароль".
 */
public class AdminLoginPage extends BasePage {

    private final Locator usernameInput;
    private final Locator passwordInput;
    private final Locator submitButton;
    private final Locator errorMessage;

    public AdminLoginPage(Page page) {
        super(page);
        this.usernameInput = page.getByPlaceholder("username");
        this.passwordInput = page.locator("input[type=\"password\"]");
        this.submitButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"));
        // Точный локатор сообщения об ошибке пока не подтверждён на реальном
        // стенде (нет доступа к админ-аккаунту для негативного сценария) —
        // это временный, максимально общий локатор алерта. Уточнить и
        // сузить, когда появятся тестовые креды администратора.
        this.errorMessage = page.getByRole(AriaRole.ALERT);
    }

    @Step("Открыть страницу логина админ-панели")
    public AdminLoginPage open() {
        page.navigate(EnvConfig.adminBaseUrl());
        usernameInput.waitFor();
        return this;
    }

    @Step("Войти в админ-панель под логином {username}")
    public AdminDashboardPage login(String username, String password) {
        usernameInput.fill(username);
        passwordInput.fill(password);
        submitButton.click();
        return new AdminDashboardPage(page);
    }

    public Locator errorMessage() {
        return errorMessage;
    }

    public Locator submitButton() {
        return submitButton;
    }
}
