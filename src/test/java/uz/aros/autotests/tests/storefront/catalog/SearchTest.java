package uz.aros.autotests.tests.storefront.catalog;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.aros.autotests.annotations.P1;
import uz.aros.autotests.annotations.P2;
import uz.aros.autotests.annotations.P3;
import uz.aros.autotests.annotations.Smoke;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.pages.storefront.CatalogPage;
import uz.aros.autotests.pages.storefront.HomePage;
import uz.aros.autotests.pages.storefront.ProductPage;
import uz.aros.autotests.pages.storefront.components.ProductCard;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты поиска товаров на витрине.
 *
 * <h2>Почему тесты ничего не хардкодят</h2>
 * Ни в одном тесте здесь нет захардкоженного названия товара, ID или
 * ключевого слова вроде {@code search("iPhone 15")}. Вместо этого каждый
 * тест сначала берёт {@link HomePage#firstProductCard()} — ЛЮБОЙ товар,
 * который реально существует на сайте в момент запуска теста, — и уже с
 * его данными (см. {@link ProductCard#pvId()}, {@link ProductCard#slugKeyword()},
 * {@link ProductCard#slugPhrase()}) работает дальше.
 *
 * <p>Почему это важно: каталог интернет-магазина живой — товары
 * заканчиваются, снимаются с продажи, переименовываются. Тест с
 * захардкоженным "iPhone 15 Pro Max" в какой-то момент начнёт падать
 * просто потому, что такого товара больше нет на стенде, и это никак не
 * связано с реальной поломкой поиска. Тест, который сам находит себе
 * товар для проверки, устойчив к таким изменениям каталога.
 */
@Epic("Storefront")
@Feature("Поиск товаров")
@Tag("catalog")
public class SearchTest extends BaseUiTest {

    @Test
    @Tag("SRCH-001")
    @P1
    @Smoke
    @DisplayName("SRCH-001 · Поиск по названию реального товара находит именно его")
    void searchingByRealProductNameFindsThatProduct() {
        HomePage homePage = new HomePage(page).open();
        ProductCard sourceProduct = homePage.firstProductCard();
        String expectedPvId = sourceProduct.pvId();
        String query = sourceProduct.slugPhrase();

        CatalogPage results = homePage.searchBar().search(query);

        results.assertHasResults();
        Optional<ProductCard> found = results.findByPvId(expectedPvId);
        assertTrue(found.isPresent(),
                "Среди результатов поиска по \"" + query + "\" не нашёлся товар с pvId=" + expectedPvId
                        + ", хотя запрос был построен из названия именно этого товара");
    }

    @Test
    @Tag("SRCH-002")
    @P2
    @DisplayName("SRCH-002 · Все результаты поиска релевантны запросу")
    void searchResultsAreRelevantToQuery() {
        HomePage homePage = new HomePage(page).open();
        ProductCard sourceProduct = homePage.firstProductCard();
        // Берём только первое слово из названия товара — такой широкий
        // запрос обычно возвращает НЕСКОЛЬКО товаров одной категории,
        // и это позволяет проверить релевантность выдачи, а не только
        // один конкретный товар.
        String keyword = sourceProduct.slugKeyword();

        CatalogPage results = homePage.searchBar().search(keyword);

        results.assertEveryResultMatchesKeyword(keyword);
    }

    @Test
    @Tag("SRCH-003")
    @P2
    @DisplayName("SRCH-003 · Поиск по несуществующему запросу показывает пустую выдачу")
    void searchingUnknownTermShowsEmptyState() {
        HomePage homePage = new HomePage(page).open();
        // Случайная строка гарантированно не совпадёт ни с одним товаром —
        // генерируется на лету, а не захардкожена, так что не может случайно
        // совпасть с реальным названием товара, появившимся на сайте позже.
        //
        // ВАЖНО: строка составлена только из букв и цифр, БЕЗ настоящих
        // английских слов. Эмпирически было замечено, что поиск сайта не
        // строго "точный": запрос вроде "no-such-product-<uuid>" — хотя
        // тоже случайный и бессмысленный — из-за слова "product" внутри
        // иногда попадал под нечёткое совпадение и возвращал общую выдачу
        // вместо пустого результата. Строка без словарных слов на практике
        // всегда даёт настоящее "нет товаров".
        String randomQuery = "zzz" + UUID.randomUUID().toString().replace("-", "");

        CatalogPage results = homePage.searchBar().search(randomQuery);

        results.assertNoResults();
    }

    @Test
    @Tag("SRCH-004")
    @P1
    @DisplayName("SRCH-004 · Клик по результату поиска открывает страницу именно этого товара")
    void openingSearchResultNavigatesToMatchingProduct() {
        HomePage homePage = new HomePage(page).open();
        ProductCard sourceProduct = homePage.firstProductCard();
        String expectedPvId = sourceProduct.pvId();
        String query = sourceProduct.slugPhrase();

        CatalogPage results = homePage.searchBar().search(query);
        ProductCard cardInResults = results.findByPvId(expectedPvId)
                .orElseThrow(() -> new AssertionError(
                        "Товар с pvId=" + expectedPvId + " не найден в результатах поиска по \"" + query + "\""));

        ProductPage productPage = cardInResults.open();

        productPage.assertOpenedProductWithPvId(expectedPvId);
    }
}
