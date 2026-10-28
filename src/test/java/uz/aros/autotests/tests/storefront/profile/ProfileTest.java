package uz.aros.autotests.tests.storefront.profile;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.AuthenticatedUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.i18n.Text;
import uz.aros.autotests.pages.storefront.components.Header;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Профиль: личные данные, меню разделов, окно удаления аккаунта (подтверждение НЕ нажимается). */
@Epic("Storefront")
@Feature("Личные данные и профиль")
@Tag("profile")
public class ProfileTest extends AuthenticatedUiTest {

    @Test
    @Tag("PRF-001")
    @P1
    @Smoke
    @DisplayName("PRF-001 · Личные данные: имя, фамилия, телефон, пол и статус «Мастер»")
    void personalDataIsShown() {
        page.navigate(EnvConfig.baseUrl() + "/profile/account");

        for (String label : new String[]{"Имя", "Фамилия", "Номер телефона", "Пол", "Мастер"}) {
            assertThat(page.getByText(label).first()).isVisible();
        }
    }

    @ParameterizedTest(name = "PRF-010 · пункт меню «{0}» → {1}")
    @CsvSource({
            "Мои заказы,/profile/orders",
            "Баллы,/profile/transactions",
            "Уведомления,/profile/notifications",
            "Мои запросы,/profile/request",
            "Спецзаказ,/profile/sales-window"})
    @Tag("PRF-010")
    @P2
    @DisplayName("PRF-010 · Пункты меню профиля открывают свои разделы")
    void profileMenuNavigates(String item, String expectedPath) {
        page.navigate(EnvConfig.baseUrl() + "/profile");

        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(item).setExact(true)).click();

        page.waitForURL(url -> url.contains(expectedPath));
        assertThat(page.getByText(item).first()).isVisible();
    }

    @Test
    @Tag("PRF-010")
    @P2
    @DisplayName("PRF-010 · Пункт «Мой кошелек» открывает раздел кошелька")
    void walletMenuItemNavigates() {
        page.navigate(EnvConfig.baseUrl() + "/profile");

        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Text.MY_WALLET.get()).setExact(true)).click();

        page.waitForURL(url -> url.contains("/profile/") && !url.endsWith("/profile"));
        Assertions.assertFalse(page.url().contains("/404"), "Раздел кошелька вернул 404: " + page.url());
    }

    @Test
    @Tag("PRF-013")
    @P3
    @DisplayName("PRF-013 · Окно «Удалить аккаунт» можно закрыть без удаления")
    void deleteAccountDialogCanBeDismissed() {
        page.navigate(EnvConfig.baseUrl() + "/profile");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Text.DELETE_ACCOUNT.get())).click();
        assertThat(page.getByRole(AriaRole.DIALOG)).isVisible();

        // ВАЖНО: подтверждающую кнопку не нажимаем никогда — закрываем окно клавишей Escape.
        page.keyboard().press("Escape");

        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(0);
        assertThat(new Header(page).profileLink()).isVisible();
    }
}
