package uz.aros.autotests.tests.storefront.auth;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.AuthenticatedUiTest;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.i18n.Text;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.Header;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Сессия вошедшего пользователя: сохранение после перезагрузки/в новой вкладке и выход.
 *
 * <p>Тесты стартуют уже залогиненными (см. {@link AuthenticatedUiTest}) — сам вход проверяет {@link LoginTest}.
 */
@Epic("Storefront")
@Feature("Авторизация")
@Tag("auth")
public class SessionTest extends AuthenticatedUiTest {

    @Test
    @Tag("AUTH-012")
    @P1
    @DisplayName("AUTH-012 · Сессия сохраняется после перезагрузки и в новой вкладке")
    void sessionSurvivesReloadAndNewTab() {
        HomePage home = new HomePage(page).open();
        home.assertUserIsLoggedIn();

        page.reload();
        new HomePage(page).assertUserIsLoggedIn();

        // Новая вкладка в том же браузерном контексте = тот же «браузер» пользователя: cookies/localStorage общие.
        Page secondTab = page.context().newPage();
        secondTab.navigate(EnvConfig.baseUrl());
        assertThat(new Header(secondTab).profileLink()).isVisible();
        secondTab.close();
    }

    /** Выход «как обычный пользователь»: с главной кликаем по имени в шапке и выходим из меню профиля. */
    private void logoutViaUi() {
        new HomePage(page).open().assertUserIsLoggedIn();
        new Header(page).profileLink().click();
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Text.LOGOUT.get())).click();

        // Если сайт спрашивает подтверждение — подтверждаем последней кнопкой диалога (обычно это «Да/Выйти»).
        page.waitForTimeout(700);
        if (page.getByRole(AriaRole.DIALOG).isVisible()) {
            page.getByRole(AriaRole.DIALOG).getByRole(AriaRole.BUTTON).last().click();
        }
    }

    @Test
    @Tag("AUTH-013")
    @P1
    @Smoke
    @DisplayName("AUTH-013 · Выход из системы возвращает шапку в состояние «гость»")
    void logoutReturnsToGuestState() {
        logoutViaUi();

        assertThat(new Header(page).signInButton()).isVisible();
        assertThat(new Header(page).profileLink()).hasCount(0);
    }

    /**
     * Дополнительная проверка (в Excel её не было). Найдено при автоматизации: если открыть /profile ПРЯМОЙ ссылкой
     * (новая вкладка, закладка) и нажать «Выйти из системы», сайт остаётся на пустой странице about:blank
     * (похоже, выход делает «назад по истории», а истории нет).
     */
    @Test
    @Tag("AUTH-X02")
    @Tag("known-defect")
    @P2
    @DisplayName("AUTH-X02 · Выход на странице профиля, открытой прямой ссылкой, не оставляет пустой экран")
    void logoutFromDirectlyOpenedProfileDoesNotLeaveBlankPage() {
        page.navigate(EnvConfig.baseUrl() + "/profile");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Text.LOGOUT.get())).click();
        page.waitForTimeout(2500);

        org.junit.jupiter.api.Assertions.assertFalse("about:blank".equals(page.url()),
                "ДЕФЕКТ: после выхода открылась пустая страница about:blank (профиль был открыт прямой ссылкой)");
    }
}
