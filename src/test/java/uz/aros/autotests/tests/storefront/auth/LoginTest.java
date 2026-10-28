package uz.aros.autotests.tests.storefront.auth;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.SignInForm;
import uz.aros.autotests.pages.storefront.components.Toast;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Тесты авторизации на витрине (вход по номеру телефона + паролю).
 *
 * <p>Класс наследуется от {@link BaseUiTest} — оттуда он получает готовое,
 * уже открытое поле {@code page} перед каждым тестом и автоматическую
 * очистку браузера после. Сам тест не управляет браузером напрямую — вся
 * работа с UI идёт через Page Object'ы ({@link HomePage}, {@link SignInForm},
 * {@link Toast}), а тест лишь описывает бизнес-сценарий: что нажать и что
 * должно получиться.
 *
 * <p>{@code @Tag(...)} — это аналог маркеров pytest (smoke/regression) из
 * прошлой версии на Python. Позволяет запускать не все тесты подряд, а
 * только нужное подмножество, например:
 * <pre>{@code mvn test -Dgroups=smoke}</pre>
 */
@Epic("Storefront")
@Feature("Авторизация")
@Tag("auth")
public class LoginTest extends BaseUiTest {

    @Test
    @Tag("AUTH-001")
    @P1
    @Smoke
    @DisplayName("AUTH-001 · Успешный вход с валидными телефоном и паролем")
    void successfulLoginWithValidCredentials() {
        HomePage homePage = new HomePage(page).open();
        SignInForm form = homePage.openSignInForm();

        form.signInWithPassword(EnvConfig.testUserPhone(), EnvConfig.testUserPassword());

        // Проверяем успех ДВУМЯ независимыми способами: всплывающим
        // уведомлением и фактическим состоянием шапки сайта. Если что-то
        // из этого изменится в UI, второй сигнал подстрахует тест.
        new Toast(page).expectSuccess(null);
        homePage.assertUserIsLoggedIn();
    }

    @Test
    @Tag("AUTH-002")
    @P1
    @DisplayName("AUTH-002 · Вход не проходит с неверным паролем")
    void loginFailsWithWrongPassword() {
        HomePage homePage = new HomePage(page).open();
        SignInForm form = homePage.openSignInForm();

        form.signInWithPassword(EnvConfig.testUserPhone(), EnvConfig.testUserWrongPassword());

        new Toast(page).expectNoSuccess();
        assertThat(form.passwordInput()).isVisible();
    }

    /**
     * {@code @ParameterizedTest} запускает один и тот же метод несколько
     * раз — по одному разу на каждое значение из {@code @ValueSource}.
     * Это избавляет от копирования одного и того же теста четыре раза
     * с разными входными данными.
     */
    @ParameterizedTest(name = "AUTH-003 · Неверный формат телефона: \"{0}\"")
    @Tag("AUTH-003")
    @P2
    @ValueSource(strings = {
            "+998",                 // только код страны, без номера
            "+998123",              // слишком короткий номер
            "+998999999999999999",  // слишком длинный номер
            "abc998987882"          // буквы вместо цифр
    })
    @DisplayName("AUTH-003 · Вход не проходит с некорректным форматом телефона")
    void loginFailsWithInvalidPhoneFormat(String invalidPhone) {
        HomePage homePage = new HomePage(page).open();
        SignInForm form = homePage.openSignInForm();

        form.signInWithPassword(invalidPhone, EnvConfig.testUserPassword());

        new Toast(page).expectNoSuccess();
    }

    @Test
    @Tag("AUTH-004")
    @P2
    @DisplayName("AUTH-004 · Вход не проходит с пустым паролем")
    void loginFailsWithEmptyPassword() {
        HomePage homePage = new HomePage(page).open();
        SignInForm form = homePage.openSignInForm();
        form.switchToPasswordLogin().fillPhone(EnvConfig.testUserPhone());

        form.submit();

        new Toast(page).expectNoSuccess();
        assertThat(form.passwordInput()).isVisible();
    }

    @Test
    @Tag("AUTH-005")
    @P2
    @DisplayName("AUTH-005 · Вход не проходит с пустым номером телефона")
    void loginFailsWithEmptyPhoneNumber() {
        HomePage homePage = new HomePage(page).open();
        SignInForm form = homePage.openSignInForm();
        form.switchToPasswordLogin()
                .fillPhone("")
                .fillPassword(EnvConfig.testUserPassword());

        form.submit();

        new Toast(page).expectNoSuccess();
    }

    @Test
    @Tag("AUTH-006")
    @P3
    @DisplayName("AUTH-006 · Пароль скрыт по умолчанию, переключатель показывает/скрывает его")
    void passwordIsMaskedByDefaultAndToggleRevealsIt() {
        HomePage homePage = new HomePage(page).open();
        SignInForm form = homePage.openSignInForm();
        form.switchToPasswordLogin().fillPassword("Any12345");

        form.assertPasswordIsMasked();

        form.togglePasswordVisibility();
        form.assertPasswordIsRevealed();

        form.togglePasswordVisibility();
        form.assertPasswordIsMasked();
    }

    @Test
    @Tag("AUTH-007")
    @P2
    @DisplayName("AUTH-007 · Переключение между режимами входа: по телефону и по паролю")
    void switchingBetweenPhoneAndPasswordLoginModes() {
        HomePage homePage = new HomePage(page).open();
        SignInForm form = homePage.openSignInForm();
        assertThat(form.passwordInput()).hasCount(0);

        form.switchToPasswordLogin();
        assertThat(form.passwordInput()).isVisible();
        assertThat(form.forgotPasswordButton()).isVisible();

        form.switchToPhoneLogin();
        assertThat(form.passwordInput()).hasCount(0);
    }
}
