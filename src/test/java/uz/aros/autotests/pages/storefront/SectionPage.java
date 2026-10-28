package uz.aros.autotests.pages.storefront;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import uz.aros.autotests.base.BasePage;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.pages.storefront.components.ProductCard;

import java.util.List;

/** Страница раздела главной: «Товары со скидкой» (/sections/onsale), «Новинки» (/sections/new), «Популярное» (/sections/popular). */
public class SectionPage extends BasePage {

    private final Locator productLinks;

    public SectionPage(Page page) {
        super(page);
        this.productLinks = page.locator("a[href*=\"/product/\"]:visible");
    }

    @Step("Открыть раздел /sections/{slug}")
    public SectionPage open(String slug) {
        page.navigate(EnvConfig.baseUrl() + "/sections/" + slug);
        productLinks.first().waitFor();
        return this;
    }

    /** Ждёт, пока в разделе появятся карточки (для случаев, когда страницу открыл не {@link #open}, а клик). */
    public SectionPage waitUntilLoaded() {
        productLinks.first().waitFor();
        return this;
    }

    public Locator heading() {
        return page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING).first();
    }

    public List<ProductCard> productCards() {
        return ProductCard.allFrom(page, productLinks);
    }
}
