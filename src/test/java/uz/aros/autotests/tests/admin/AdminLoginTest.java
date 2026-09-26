package uz.aros.autotests.tests.admin;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.admin.AdminDashboardPage;
import uz.aros.autotests.pages.admin.AdminLoginPage;

import java.util.function.Supplier;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Тесты авторизации в админ-панели (staging-admin.aros.uz).
 *
 * <p><b>Пока не подключены реальные креды администратора.</b> Метод
 * {@link #adminCredentialsRequired()} перед каждым тестом проверяет, заданы
 * ли {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD}, и если нет — тест
 * аккуратно ПРОПУСКАЕТСЯ (жёлтый статус "skipped" в отчёте), а не падает
 * красным. Это тот же приём, что использовала фикстура {@code valid_credentials}
 * в прошлой Python-версии через {@code pytest.skip(...)}.
 *
 * <p>Как включить эти тесты: добавьте в {@code .env}
 * <pre>{@code
 * ADMIN_USERNAME=...
 * ADMIN_PASSWORD=...
 * }</pre>
 * и тесты начнут реально выполняться — без единой правки кода.
 */
@Epic("Admin panel")
@Feature("Авторизация")
@Tag("auth")
public class AdminLoginTest extends BaseUiTest {

    @BeforeEach
    void adminCredentialsRequired() {
        boolean credentialsPresent = isReadable(EnvConfig::adminUsername) && isReadable(EnvConfig::adminPassword);
        Assumptions.assumeTrue(credentialsPresent,
                "ADMIN_USERNAME / ADMIN_PASSWORD не заданы — тесты админ-панели пропущены. "
                        + "Заполните переменные в .env, когда появится тестовый аккаунт администратора.");
    }

    @Test
    @Tag("smoke")
    @DisplayName("Успешный вход в админ-панель с валидными учётными данными")
    void successfulLogin() {
        AdminLoginPage loginPage = new AdminLoginPage(page).open();

        AdminDashboardPage dashboard = loginPage.login(EnvConfig.adminUsername(), EnvConfig.adminPassword());

        dashboard.assertLoggedIn();
    }

    @Test
    @Tag("regression")
    @DisplayName("Вход в админ-панель не проходит с неверным паролем")
    void loginFailsWithWrongPassword() {
        AdminLoginPage loginPage = new AdminLoginPage(page).open();

        loginPage.login(EnvConfig.adminUsername(), "definitely-wrong-password-123");

        // После неудачного логина форма должна остаться на экране —
        // самый надёжный, не зависящий от точного текста ошибки признак.
        assertThat(loginPage.submitButton()).isVisible();
    }

    /** Небольшой хелпер: пытается прочитать обязательную переменную конфигурации, не бросая исключение наружу. */
    private static boolean isReadable(Supplier<String> requiredConfigValue) {
        try {
            requiredConfigValue.get();
            return true;
        } catch (IllegalStateException notConfigured) {
            return false;
        }
    }
}
