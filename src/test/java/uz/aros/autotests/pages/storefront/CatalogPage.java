package uz.aros.autotests.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import org.junit.jupiter.api.Assertions;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.i18n.Text;
import uz.aros.autotests.pages.storefront.components.ProductCard;

import java.util.List;
import java.util.Optional;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object страницы каталога/результатов поиска ({@code /filter?query=...}
 * или просто {@code /filter} для полного каталога без фильтра).
 *
 * <p>Он ничего не знает, ПО какому запросу открылся — это специально:
 * страница либо показывает сетку товаров, либо показывает состояние
 * "нет товаров". Конкретный сценарий (что искали и что ожидаем) описывает
 * тест, а не этот класс.
 */
public class CatalogPage extends BasePage {

    private final Locator productLinks;
    private final Locator emptyStateMessage;

    public CatalogPage(Page page) {
        super(page);
        // :visible — см. подробное объяснение в HomePage: всплывающее
        // уведомление "Product added to cart!" оставляет свою ссылку на
        // товар в DOM даже после того, как скрывается, и без фильтра по
        // видимости она попадала бы в список результатов и сдвигала индексы.
        this.productLinks = page.locator("a[href*=\"/product/\"]:visible");
        this.emptyStateMessage = page.getByText(Text.CATALOG_EMPTY.get());
    }

    /**
     * Дожидается, пока страница определится с результатом: либо появится
     * хотя бы одна карточка товара, либо покажется сообщение "нет товаров".
     * Вызывать сразу после перехода на эту страницу, перед любыми
     * проверками — иначе можно случайно проверить пустой промежуточный
     * кадр, пока данные ещё грузятся.
     */
    @Step("Дождаться загрузки результатов каталога")
    public CatalogPage waitUntilLoaded() {
        // Запас по времени: поиск на staging иногда отвечает 5–10 секунд.
        productLinks.first().or(emptyStateMessage).waitFor(new Locator.WaitForOptions().setTimeout(25_000));
        return this;
    }

    /** Все товары на текущей странице выдачи, в порядке отображения. */
    public List<ProductCard> productCards() {
        return ProductCard.allFrom(page, productLinks);
    }

    @Step("Проверить, что выдача не пустая")
    public void assertHasResults() {
        assertThat(productLinks.first()).isVisible();
    }

    @Step("Проверить, что выдача пустая")
    public void assertNoResults() {
        assertThat(emptyStateMessage).isVisible();
    }

    /**
     * Проверяет, что название КАЖДОГО товара в выдаче содержит переданное
     * ключевое слово (без учёта регистра). Используется для проверки
     * релевантности поиска: если искали по слову, которое реально есть в
     * названии одного из товаров сайта, разумно ожидать, что ВСЕ найденные
     * товары так или иначе про него же.
     */
    @Step("Проверить, что все результаты релевантны запросу \"{keyword}\"")
    public void assertEveryResultMatchesKeyword(String keyword) {
        String needle = keyword.toLowerCase();
        List<ProductCard> cards = productCards();
        Assertions.assertFalse(cards.isEmpty(), "Ожидались результаты поиска по \"" + keyword + "\", но выдача пуста");
        for (ProductCard card : cards) {
            String haystack = card.slugPhrase().toLowerCase();
            Assertions.assertTrue(haystack.contains(needle),
                    "Товар \"" + haystack + "\" не содержит ключевое слово \"" + keyword + "\", "
                            + "хотя попал в результаты поиска по этому слову");
        }
    }

    /** Ищет среди результатов товар с конкретным pvId — пригодится, чтобы найти "тот самый" товар в выдаче. */
    public Optional<ProductCard> findByPvId(String pvId) {
        return productCards().stream()
                .filter(card -> card.pvId().equals(pvId))
                .findFirst();
    }

    // ------------------------------------------------------------------
    // Фильтры, сортировка, «Показать ещё» (кейсы CAT-*, SRCH-009)
    // ------------------------------------------------------------------

    /** Открывает каталог с готовым поисковым запросом по прямой ссылке (быстрее, чем набирать в строке поиска). */
    @Step("Открыть каталог с запросом \"{query}\"")
    public CatalogPage openWithQuery(String query) {
        page.navigate(EnvConfig.baseUrl() + "/filter?query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8));
        return waitUntilLoaded();
    }

    public Locator onlyInStockToggle() {
        return page.getByRole(com.microsoft.playwright.options.AriaRole.CHECKBOX,
                new Page.GetByRoleOptions().setName(Text.ONLY_IN_STOCK.get()));
    }

    public Locator clearFiltersButton() {
        return page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Text.CLEAR_FILTERS.get()));
    }

    public Locator showMoreButton() {
        return page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Text.SHOW_MORE.get()));
    }

    /** Чекбокс фильтра по названию (бренд, категория, цвет, качество). */
    public Locator filterCheckbox(String label) {
        return page.getByRole(com.microsoft.playwright.options.AriaRole.CHECKBOX,
                new Page.GetByRoleOptions().setName(label).setExact(true));
    }

    /** Поля цены «от» и «до» (текстовые инпуты MUI с числом внутри). */
    public Locator priceInputs() {
        return page.locator("[class*=MuiInputBase] input[type=\"text\"]");
    }

    @Step("Выбрать сортировку: {option}")
    public void sortBy(Text option) {
        Locator sortButton = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Text.SORT.get()));
        Locator optionItem = page.getByText(option.get(), new Page.GetByTextOptions().setExact(true));
        // Первый клик иногда приходится на ещё не «ожившую» (не гидратированную) кнопку и меню не открывается —
        // поэтому до трёх попыток открыть меню.
        for (int attempt = 0; attempt < 3 && !optionItem.isVisible(); attempt++) {
            sortButton.click();
            try {
                optionItem.waitFor(new Locator.WaitForOptions().setTimeout(2_500));
            } catch (com.microsoft.playwright.TimeoutError notOpenedYet) {
                // следующая попытка
            }
        }
        optionItem.click();
        settle();
    }

    /** Список обновляется асинхронно, а явного индикатора загрузки нет — даём сайту короткую паузу и ждём карточки. */
    public void settle() {
        page.waitForTimeout(1500);
        productLinks.first().or(emptyStateMessage).waitFor();
    }

    /** Цены карточек (без нулевых — это товары «под заказ» без цены), в порядке показа. */
    public List<Long> visiblePrices() {
        return productCards().stream().map(ProductCard::currentPrice).filter(price -> price > 0).toList();
    }

    public int cardsCount() {
        return productLinks.count();
    }
}
