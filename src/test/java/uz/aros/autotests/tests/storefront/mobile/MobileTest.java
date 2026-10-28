package uz.aros.autotests.tests.storefront.mobile;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.base.MobileUiTest;
import uz.aros.autotests.config.EnvConfig;

/** Мобильная версия (эмуляция телефона 390×844). */
@Epic("Storefront")
@Feature("Мобильная версия")
@Tag("mobile")
public class MobileTest extends MobileUiTest {

    @ParameterizedTest(name = "MOB-004 · {0}")
    @ValueSource(strings = {"", "/sections/new", "/filter?query=akkumulyator", "/cart"})
    @Tag("MOB-004")
    @P3
    @DisplayName("MOB-004 · На телефоне нет горизонтальной прокрутки")
    void noHorizontalScroll(String path) {
        page.navigate(EnvConfig.baseUrl() + path);
        page.waitForTimeout(2500);

        Number overflow = (Number) page.evaluate(
                "document.documentElement.scrollWidth - document.documentElement.clientWidth");
        Assertions.assertTrue(overflow.intValue() <= 1,
                "Страница " + path + " шире экрана телефона на " + overflow + " px (появляется горизонтальная прокрутка)");
    }
}
