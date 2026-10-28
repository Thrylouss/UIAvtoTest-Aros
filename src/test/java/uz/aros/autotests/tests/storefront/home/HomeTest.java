package uz.aros.autotests.tests.storefront.home;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.ProductPage;
import uz.aros.autotests.pages.storefront.SectionPage;
import uz.aros.autotests.pages.storefront.components.ProductCard;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Главная страница: секции, переходы «Все», содержимое и корректность карточек товаров. */
@Epic("Storefront")
@Feature("Главная страница")
@Tag("home")
public class HomeTest extends BaseUiTest {

    @Test
    @Tag("HOME-001")
    @P1
    @Smoke
    @DisplayName("HOME-001 · Главная загружается со всеми секциями и товарами")
    void homeLoadsWithAllSections() {
        HomePage home = new HomePage(page).open();

        for (String slug : new String[]{"onsale", "new", "popular"}) {
            assertThat(page.locator("a[href$=\"/sections/" + slug + "\"]").first())
                    .isVisible();
        }
        Assertions.assertTrue(home.productCards().size() >= 10,
                "На главной ожидалось не меньше 10 карточек товаров, найдено: " + home.productCards().size());
    }

    @ParameterizedTest(name = "HOME-00{0} · раздел {1}")
    @CsvSource({"3,onsale", "4,new", "5,popular"})
    @Tag("HOME-003")
    @Tag("HOME-004")
    @Tag("HOME-005")
    @P2
    @DisplayName("HOME-003..005 · Ссылка «Все» открывает страницу раздела со списком товаров")
    void allLinkOpensSectionPage(int caseNo, String slug) {
        new HomePage(page).open();

        page.locator("a[href$=\"/sections/" + slug + "\"]").first().click();

        page.waitForURL(url -> url.contains("/sections/" + slug));
        SectionPage section = new SectionPage(page).waitUntilLoaded();
        Assertions.assertFalse(section.productCards().isEmpty(), "Раздел " + slug + " пуст");
    }

    @Test
    @Tag("HOME-006")
    @P2
    @DisplayName("HOME-006 · Карточка товара содержит название с ID, цену и кнопку действия")
    void productCardContainsMainElements() {
        HomePage home = new HomePage(page).open();
        ProductCard card = home.firstProductCard();

        Assertions.assertTrue(card.title().matches(".*\\(ID:\\s*\\d+\\).*"), "В названии нет «(ID: число)»: " + card.title());
        Assertions.assertFalse(card.prices().isEmpty(), "На карточке не показана цена в сумах");
        assertThat(card.root().locator("xpath=ancestor::div[.//button][1]").locator("button").first()).isVisible();
    }

    @Test
    @Tag("HOME-007")
    @P2
    @DisplayName("HOME-007 · Процент скидки на бейдже совпадает с разницей старой и новой цены")
    void discountBadgeMatchesPrices() {
        new SectionPage(page).open("new");
        List<ProductCard> cards = new SectionPage(page).productCards();

        int checked = 0;
        for (ProductCard card : cards) {
            int badge = card.discountPercent();
            List<Long> prices = card.prices();
            if (badge < 0 || prices.size() < 2) {
                continue;
            }
            long oldPrice = prices.get(0);
            long newPrice = prices.get(prices.size() - 1);
            long computed = Math.round((oldPrice - newPrice) * 100.0 / oldPrice);
            Assertions.assertTrue(Math.abs(computed - badge) <= 1,
                    "Товар «" + card.title() + "»: на бейдже -" + badge + "%, а по ценам " + oldPrice + " → " + newPrice
                            + " скидка " + computed + "%");
            checked++;
        }
        Assumptions.assumeTrue(checked > 0, "В разделе «Новинки» сейчас нет товаров со скидкой — проверять нечего");
    }

    @Test
    @Tag("HOME-008")
    @P1
    @DisplayName("HOME-008 · Клик по карточке открывает страницу этого товара")
    void clickingCardOpensProductPage() {
        HomePage home = new HomePage(page).open();
        ProductCard card = home.firstProductCard();
        String idInTitle = card.title().replaceAll(".*\\(ID:\\s*(\\d+)\\).*", "$1");

        card.open();

        page.waitForURL(url -> url.contains("/product/"));
        assertThat(new ProductPage(page).title()).containsText("(ID: " + idInTitle + ")");
    }
}
