package uz.aros.autotests.tests.storefront.l10n;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.base.AuthenticatedUiTest;
import uz.aros.autotests.config.EnvConfig;

import java.util.regex.Pattern;

/** Проверки локализации страниц, доступных только вошедшему пользователю. */
@Epic("Storefront")
@Feature("Локализация")
@Tag("l10n")
public class LocalizationAuthTest extends AuthenticatedUiTest {

    @Test
    @Tag("L10N-003")
    @Tag("known-defect")
    @P2
    @DisplayName("L10N-003 · В заказах на /ru нет «сырых» технических статусов на английском")
    void ordersHaveNoRawEnglishStatuses() {
        page.navigate(EnvConfig.baseUrl() + "/profile/orders");
        page.getByText("Активные").first().waitFor();
        page.getByText("Все", new com.microsoft.playwright.Page.GetByTextOptions().setExact(true)).first().click();
        page.waitForTimeout(1500);

        String body = page.locator("body").innerText();
        boolean raw = Pattern.compile("(?m)^(canceled|cancelled|pending|paid|unpaid|completed|processing)$",
                Pattern.CASE_INSENSITIVE).matcher(body).find();
        Assertions.assertFalse(raw, "ДЕФЕКТ перевода: в заказах виден технический статус оплаты на английском (например, «canceled»)");
    }
}
