package uz.aros.autotests.tests.storefront.catalog;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.components.Header;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Подбор по модели устройства: окно «Выберите бренд» → «Выберите модель» → «Выберите категорию» → список товаров. */
@Epic("Storefront")
@Feature("Подбор по модели")
@Tag("models")
public class ModelPickerTest extends BaseUiTest {

    private Locator openDialog() {
        new HomePage(page).open();
        return new Header(page).openModelDialog();
    }

    private Locator step(Locator dialog, String heading) {
        return dialog.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName(heading));
    }

    @Test
    @Tag("MDL-002")
    @P3
    @DisplayName("MDL-002 · Поиск бренда в окне сужает список")
    void brandSearchNarrowsList() {
        Locator dialog = openDialog();

        dialog.getByRole(AriaRole.TEXTBOX).fill("sams");

        assertThat(dialog.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Samsung"))).isVisible();
        assertThat(dialog.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Apple"))).hasCount(0);
    }

    @Test
    @Tag("MDL-003")
    @P2
    @DisplayName("MDL-003 · Выбор бренда открывает список его моделей")
    void selectingBrandShowsModels() {
        Locator dialog = openDialog();

        dialog.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Samsung").setExact(true)).click();

        assertThat(step(dialog, "Выберите модель")).isVisible();
        assertThat(dialog.getByText("Galaxy").first()).isVisible();
    }

    @Test
    @Tag("MDL-004")
    @P1
    @DisplayName("MDL-004 · Бренд → модель → категория приводят к списку товаров")
    void brandModelCategoryLeadsToProductList() {
        Locator dialog = openDialog();
        dialog.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Samsung").setExact(true)).click();
        step(dialog, "Выберите модель").waitFor();
        dialog.getByRole(AriaRole.HEADING).filter(new Locator.FilterOptions().setHasText("Galaxy")).first().click();
        step(dialog, "Выберите категорию").waitFor();

        // Категорию берём по названию: «Аккумуляторы» есть у каждой модели телефона. (Первая в списке категория
        // у некоторых моделей пустая, и клик по ней молча ничего не делает — см. наблюдения в «Замечаниях».)
        dialog.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Аккумуляторы").setExact(true)).click();

        page.waitForURL(url -> url.contains("/filter?") && url.contains("product_models="),
                new Page.WaitForURLOptions().setTimeout(20_000));
        assertThat(page.locator("a[href*=\"/product/\"]:visible").first()).isVisible();
    }

    @Test
    @Tag("MDL-005")
    @P3
    @DisplayName("MDL-005 · Кнопка «Назад» возвращает к брендам, «Закрыть» закрывает окно")
    void backAndCloseButtonsWork() {
        Locator dialog = openDialog();
        dialog.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Samsung").setExact(true)).click();
        step(dialog, "Выберите модель").waitFor();

        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("back")).click();
        assertThat(step(dialog, "Выберите бренд")).isVisible();

        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("close")).click();
        assertThat(page.getByRole(AriaRole.DIALOG)).hasCount(0);
    }
}
