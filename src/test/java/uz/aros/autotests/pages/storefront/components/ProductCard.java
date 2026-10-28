package uz.aros.autotests.pages.storefront.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import uz.aros.autotests.pages.storefront.ProductPage;

import java.util.ArrayList;
import uz.aros.autotests.pages.storefront.CartPage;
import java.util.List;

/**
 * Один товар в сетке карточек — переиспользуется и на главной странице
 * (блоки "New Products" / "Popular Products"), и на странице каталога/
 * результатов поиска: и там, и там это один и тот же React-компонент,
 * поэтому и Page Object у него один.
 *
 * <h2>Откуда берутся "динамические" данные о товаре</h2>
 * Специально НЕ парсим видимый текст карточки (название, цену) — эта
 * вёрстка мешает в одну кучу "Quick view", рейтинг, число отзывов, цену
 * и название, и надёжного локатора именно на название текста внутри
 * карточки не нашлось.
 *
 * <p>Вместо этого используем ссылку карточки (атрибут {@code href}) — она
 * ВСЕГДА присутствует и имеет предсказуемый вид:
 * <pre>/en/product/-oyna-oca-apple-iphone-16-pro-max-qora-original-id:-9179-3680?pvId=9179</pre>
 * Из неё достаём две вещи:
 * <ul>
 *   <li>{@link #pvId()} — числовой ID товара из query-параметра {@code pvId}.
 *       Это стабильный, языконезависимый идентификатор — по нему проверяем
 *       "это тот же самый товар", а не по названию (см. ниже, почему по
 *       названию — ненадёжно).</li>
 *   <li>{@link #slugKeyword()} / {@link #slugPhrase()} — "человеческое"
 *       название товара, восстановленное из slug'а ссылки. Это и есть тот
 *       самый динамический, никогда не захардкоженный текст, которым тесты
 *       пользуются как поисковым запросом: вместо `search("Samsung")` тесты
 *       берут ЛЮБОЙ реально существующий на сайте товар прямо сейчас и
 *       ищут его же.</li>
 * </ul>
 *
 * <p><b>Важное наблюдение с живого стенда:</b> название в slug'е ссылки —
 * на узбекском ("Oyna OCA...", т.е. "Стекло OCA"), а на самой странице
 * товара заголовок уже переведён на английский ("Glass OCA..."). Поиск же
 * матчит именно по исходному (узбекскому) названию. Из-за этого сравнивать
 * "название на карточке" и "заголовок на странице товара" через equals -
 * плохая идея, тест будет ложно падать. Поэтому для проверки "открылся тот
 * же товар" везде используется {@link #pvId()}, а не текст названия.
 */
public final class ProductCard {

    private final Page page;
    private final Locator root;

    /**
     * Контейнер всей карточки — шире, чем просто ссылка {@link #root}.
     *
     * <p>Оказалось, что кнопка "Добавить в корзину" (и степпер количества,
     * который её заменяет после добавления) физически лежит СНАРУЖИ
     * ссылки {@code <a>}, хотя визуально выглядит частью той же карточки:
     * это отдельный элемент-сосед на уровне общего контейнера-обёртки.
     * Найдено эмпирически через инспекцию живой страницы — по ссылке
     * {@code root.locator("button")} в режиме степпера ничего не находилось.
     * XPath ниже поднимается от ссылки вверх до ближайшего предка, который
     * СОДЕРЖИТ хоть одну кнопку — то есть до настоящего контейнера
     * карточки, независимо от того, сколько именно уровней обёрток между
     * ними в конкретной версии вёрстки.
     */
    private final Locator cardContainer;

    public ProductCard(Page page, Locator root) {
        this.page = page;
        this.root = root;
        this.cardContainer = root.locator("xpath=ancestor::div[.//button][1]");
    }

    /**
     * Кнопка "Добавить в корзину" — видна, пока товара нет в корзине.
     * Стабильный признак — CSS-класс {@code MuiButton-contained} (залитая
     * кнопка). Это класс самой библиотеки MUI, а не хэш конкретной сборки
     * (вроде {@code mui-style-xxxxx}), поэтому safe использовать как
     * локатор — та же логика, что и с классами react-toastify в {@link Toast}.
     */
    private Locator addToCartButton() {
        return cardContainer.locator("button.MuiButton-contained");
    }

    /**
     * Поле количества, которое появляется на карточке ПОСЛЕ добавления в
     * корзину, вместо кнопки.
     *
     * <p>Важная особенность: этот {@code <input>} — самый настоящий (его
     * {@code value} реально хранит количество и меняется), но он занимает
     * НОЛЬ пикселей ({@code width: 0; height: 0}), хотя визуально число
     * прекрасно видно пользователю — сайт рисует его как-то иначе, а этот
     * input существует только для логики. Из-за этого Playwright считает
     * его невидимым (это корректно и ожидаемо для его модели actionability:
     * элемент нулевого размера не может принять клик или попасть в фокус),
     * и ЖДАТЬ ЕГО ВИДИМОСТИ ({@code .waitFor()} по умолчанию) — бессмысленно,
     * оно никогда не наступит. Обнаружено эмпирически: без этого понимания
     * тест завис на 10-секундном таймауте, ожидая вечно невозможного.
     * Читать значение ({@link #quantityOnCard()}) можно и нужно как обычно
     * — {@code inputValue()} прекрасно работает и на невидимых элементах,
     * а вот кликать по нему или ждать его видимости — нет.
     */
    private Locator quantityInput() {
        return cardContainer.locator("input[type=\"number\"]");
    }

    /**
     * Кнопки "-" и "+" степпера. Оба используют класс {@code MuiButton-text}
     * (текстовая, незалитая кнопка) — этим они отличаются от залитой кнопки
     * "Добавить в корзину" ({@code MuiButton-contained}) и от кнопки
     * "избранное" ({@code MuiIconButton-root}), которая тоже лежит в этом
     * контейнере, но нам здесь не нужна.
     */
    private Locator decreaseQuantityButton() {
        return cardContainer.locator("button.MuiButton-text").first();
    }

    private Locator increaseQuantityButton() {
        return cardContainer.locator("button.MuiButton-text").last();
    }

    /** true, если товар уже в корзине (карточка показывает степпер количества, а не кнопку добавления). */
    public boolean isInCart() {
        return addToCartButton().count() == 0;
    }

    /**
     * Добавляет товар в корзину прямо с карточки (без перехода на страницу
     * товара).
     *
     * <p>После клика кнопка "Добавить в корзину" пропадает не мгновенно —
     * нужно дождаться ответа сервера и React-рендера. Дожидаемся именно
     * ИСЧЕЗНОВЕНИЯ этой кнопки (а не появления инпута количества — он
     * нулевого размера и никогда не станет "видимым", см. {@link
     * #quantityInput()}), это и есть надёжный сигнал, что карточка
     * перешла в режим степпера.
     *
     * <p>После этого сайт на пару секунд показывает всплывающее
     * уведомление "Product added to cart!" крупным баннером у верхнего
     * края страницы. Само по себе это не проблема — но если сразу после
     * этого добавлять в корзину СЛЕДУЮЩИЙ товар из той же (верхней) части
     * сетки, уведомление физически перекрывает его кнопку, и клик
     * Playwright не проходит (обнаружено эмпирически — тест падал именно
     * на второй карточке, а не на первой). Поэтому ждём, пока уведомление
     * исчезнет само, прежде чем возвращать управление тесту.
     */
    public void addToCart() {
        addToCartButton().click();
        addToCartButton().waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
        Locator notification = page.locator(".Toastify__toast--success").first();
        try {
            // Сначала дожидаемся, пока уведомление реально появится (оно
            // рендерится не мгновенно) — короткий таймаут, а не значение
            // по умолчанию: если уведомление почему-то не показалось
            // (например, успело мелькнуть быстрее, чем мы досюда дошли),
            // не хотим ждать полные 10 секунд впустую при каждом добавлении.
            notification.waitFor(new Locator.WaitForOptions().setTimeout(3_000));
            notification.waitFor(new Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
        } catch (com.microsoft.playwright.TimeoutError neverAppeared) {
            // Уведомление не появилось (или уже исчезло) — значит, и ждать
            // нечего, продолжаем как ни в чём не бывало.
        }
    }

    /** Увеличивает количество прямо на карточке (карточка уже должна быть в режиме "товар в корзине"). */
    public void increaseQuantity() {
        increaseQuantityButton().click();
    }

    /**
     * Уменьшает количество прямо на карточке. Если количество было 1,
     * товар полностью удаляется из корзины, и карточка возвращается в
     * режим кнопки "Добавить в корзину" — это поведение сайта, а не баг.
     */
    public void decreaseQuantity() {
        decreaseQuantityButton().click();
    }

    /** Текущее количество товара, показанное на карточке (карточка должна быть в режиме степпера). */
    public int quantityOnCard() {
        return Integer.parseInt(quantityInput().inputValue());
    }

    /**
     * Оборачивает КАЖДУЮ ссылку-карточку из переданного локатора в
     * {@link ProductCard}. Используется и на главной странице, и в
     * каталоге — там и там карточки товаров ищутся одинаково:
     * {@code page.locator("a[href*='/product/']")}.
     */
    public static List<ProductCard> allFrom(Page page, Locator productLinks) {
        List<ProductCard> cards = new ArrayList<>();
        int count = productLinks.count();
        for (int i = 0; i < count; i++) {
            cards.add(new ProductCard(page, productLinks.nth(i)));
        }
        return cards;
    }

    public Locator root() {
        return root;
    }

    private String href() {
        String href = root.getAttribute("href");
        if (href == null) {
            throw new IllegalStateException("У карточки товара нет атрибута href — это не ссылка на товар");
        }
        return href;
    }

    /** Числовой ID товара (параметр pvId в ссылке) — стабильный, языконезависимый идентификатор. */
    public String pvId() {
        String href = href();
        int paramStart = href.indexOf("pvId=");
        if (paramStart == -1) {
            throw new IllegalStateException("В ссылке товара не найден параметр pvId: " + href);
        }
        String tail = href.substring(paramStart + "pvId=".length());
        int ampersand = tail.indexOf('&');
        return ampersand == -1 ? tail : tail.substring(0, ampersand);
    }

    /** Путь ссылки без query-параметров, например "/-oyna-oca-apple-iphone-16-pro-max-qora-original-id:-9179-3680". */
    private String slugSegment() {
        String href = href();
        String withoutQuery = href.contains("?") ? href.substring(0, href.indexOf('?')) : href;
        int lastSlash = withoutQuery.lastIndexOf('/');
        return withoutQuery.substring(lastSlash + 1);
    }

    /**
     * Первое "слово" из slug'а ссылки — например, "oyna" для стёкол OCA.
     * Такой запрос обычно возвращает МНОГО релевантных товаров (все стёкла
     * OCA на сайте) — используется в тесте на релевантность выдачи.
     */
    public String slugKeyword() {
        for (String token : slugSegment().split("[-:]")) {
            if (!token.isBlank()) {
                return token;
            }
        }
        throw new IllegalStateException("Не удалось выделить ни одного слова из slug'а: " + slugSegment());
    }

    /**
     * Slug, превращённый обратно в читаемую фразу (дефисы → пробелы) —
     * например, "oyna oca apple iphone 16 pro max qora original id 9179 3680".
     * Достаточно специфичен, чтобы найти именно этот товар через поиск.
     */
    public String slugPhrase() {
        return slugSegment().replace('-', ' ').replace(":", "").trim().replaceAll("\\s+", " ");
    }

    /**
     * Открывает товар (клик по карточке) и возвращает Page Object страницы
     * товара.
     *
     * <p>Кликаем не по самой ссылке-карточке целиком, а по вложенному
     * заголовку (счётчику отзывов) — просто чтобы не зависеть от того,
     * какая именно часть карточки сейчас под курсором (картинка / текст /
     * кнопка даты поставки — у карточки их несколько, и не все они —
     * просто пассивный текст, см. {@link #slugPhrase()}). Сам по себе
     * переход на страницу товара — это переход клиентского роутера (SPA),
     * поэтому сразу после клика URL ещё может не обновиться; на этом
     * основана проверка в {@link uz.aros.autotests.pages.storefront.ProductPage#assertOpenedProductWithPvId}.
     */
    public ProductPage open() {
        root.getByRole(AriaRole.HEADING).click();
        return new ProductPage(page);
    }

    // ------------------------------------------------------------------
    // Данные, видимые на карточке (кейсы HOME-006/007, CAT-009/010)
    // ------------------------------------------------------------------

    /** Название товара вместе с «(ID: 1234)» — единственный абзац карточки, содержащий «(ID:». */
    public String title() {
        return root.locator("p").filter(new Locator.FilterOptions().setHasText("(ID:")).first().innerText().trim();
    }

    /** Все строки вида «155 000 сум» на карточке по порядку (у товара со скидкой их две: старая и новая цена). */
    public java.util.List<Long> prices() {
        java.util.List<Long> result = new ArrayList<>();
        for (String text : root.locator("p").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("сум|so.m|UZS", java.util.regex.Pattern.CASE_INSENSITIVE))).allInnerTexts()) {
            result.add(CartPage.digits(text));
        }
        return result;
    }

    /** Актуальная цена — последняя из показанных. 0 у товара, которого нет в наличии. */
    public long currentPrice() {
        java.util.List<Long> p = prices();
        return p.isEmpty() ? 0 : p.get(p.size() - 1);
    }

    /** Бейдж «-3%» (скидка) или -1, если бейджа нет. */
    public int discountPercent() {
        Locator badge = root.locator("p").filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^-\\d+%$")));
        if (badge.count() == 0) {
            return -1;
        }
        return Integer.parseInt(badge.first().innerText().replaceAll("[^0-9]", ""));
    }
}
