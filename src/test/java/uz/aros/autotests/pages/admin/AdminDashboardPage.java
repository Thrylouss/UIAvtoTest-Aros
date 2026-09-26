package uz.aros.autotests.pages.admin;

import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.junit.jupiter.api.Assertions;
import uz.aros.autotests.base.BasePage;

/**
 * Page Object главной страницы админ-панели после успешного входа.
 *
 * <p><b>Черновой вариант.</b> Внутренняя разметка дашборда пока не
 * исследована (для этого нужны реальные креды администратора — их ещё нет
 * в конфигурации, см. {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD} в
 * {@code .env.example}). Как только они появятся, этот класс стоит
 * дополнить локаторами конкретных элементов дашборда (меню, заголовок и
 * т.п.) — сейчас же он проверяет только самый общий и надёжный признак
 * успеха: URL страницы изменился и больше не содержит "/login".
 */
public class AdminDashboardPage extends BasePage {

    public AdminDashboardPage(Page page) {
        super(page);
    }

    @Step("Проверить, что вход в админ-панель выполнен успешно")
    public void assertLoggedIn() {
        page.waitForURL(url -> !url.contains("login"));
        Assertions.assertFalse(page.url().contains("login"),
                "После логина ожидался переход с /login на страницу дашборда, но URL остался: " + page.url());
    }
}
