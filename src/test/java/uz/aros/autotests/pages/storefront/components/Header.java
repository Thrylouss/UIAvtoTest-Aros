package uz.aros.autotests.pages.storefront.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import uz.aros.autotests.i18n.Text;

/**
 * Шапка сайта: верхняя полоса (магазин, контакты, язык) и основная часть (логотип, «Каталог», «Модель»,
 * поиск, корзина, избранное, профиль).
 *
 * <p>Где возможно, элементы ищутся по {@code href} ({@code a[href*="/cart"]}) — это не зависит от языка
 * интерфейса и от имени пользователя, в отличие от видимого текста.
 */
public class Header {

    private final Page page;
    private final Locator banner;

    public Header(Page page) {
        this.page = page;
        this.banner = page.getByRole(AriaRole.BANNER);
    }

    /** Логотип — первая ссылка в шапке. */
    public Locator logo() {
        return banner.locator("a").first();
    }

    public Locator catalogButton() {
        return banner.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Text.CATALOG.get()).setExact(true));
    }

    public Locator modelButton() {
        return banner.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Text.MODEL.get()).setExact(true));
    }

    public Locator cartLink() {
        return banner.locator("a[href*=\"/cart\"]").first();
    }

    /** Ссылка на профиль (видна только вошедшему пользователю; в ней его имя). */
    public Locator profileLink() {
        return banner.locator("a[href*=\"/profile\"]");
    }

    /** Кнопка «Вход» (видна только гостю). */
    public Locator signInButton() {
        return banner.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Text.SIGN_IN.get()).setExact(true));
    }

    /** Иконка-сердце «Избранное» — одна из иконочных кнопок между поиском и корзиной/профилем. */
    public Locator favoritesButton() {
        return banner.locator("button:not([aria-label]):has(svg)").filter(new Locator.FilterOptions().setHasNotText(Text.SIGN_IN.get())).nth(1);
    }

    /** Верхняя полоса: название выбранного магазина с выпадашкой — первый абзац страницы. */
    public Locator storeSelector() {
        return page.locator("p").first();
    }

    /** Верхняя полоса целиком (магазин + контакты + режим работы + язык). */
    public Locator topBar() {
        return page.locator("body > div").first();
    }

    public Locator languageButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(java.util.regex.Pattern.compile("^(Ru|Uz|En|O.z)$", java.util.regex.Pattern.CASE_INSENSITIVE)));
    }

    @Step("Открыть окно выбора магазина")
    public Locator openStoreDialog() {
        storeSelector().click();
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        dialog.waitFor();
        return dialog;
    }

    @Step("Открыть меню «Каталог»")
    public void openCatalogMenu() {
        catalogButton().click();
    }

    @Step("Открыть окно «Выберите бренд» (Модель)")
    public Locator openModelDialog() {
        modelButton().click();
        Locator dialog = page.getByRole(AriaRole.DIALOG);
        dialog.waitFor();
        return dialog;
    }
}
