package uz.aros.autotests.base;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import uz.aros.autotests.playwrightsupport.AuthSession;
import uz.aros.autotests.playwrightsupport.PlaywrightManager;

/**
 * Базовый класс для тестов, которым нужен ВОШЕДШИЙ пользователь (корзина, профиль, заказы, избранное…).
 *
 * <p>Каждый тест стартует с готовой сессией ({@link AuthSession}), поэтому форма входа не открывается.
 * Корзина тестового пользователя общая между прогонами, поэтому после каждого теста мы её очищаем — иначе
 * товары из одного теста «протекут» в следующий и результаты станут случайными.
 */
public abstract class AuthenticatedUiTest extends BaseUiTest {

    @Override
    protected Page createPage() {
        return PlaywrightManager.newPage(AuthSession.userState(), false);
    }

    /** Чистим корзину после теста. Если тест вообще не трогал корзину — это 1 быстрый запрос страницы. */
    @AfterEach
    void cleanCart() {
        try {
            new uz.aros.autotests.pages.storefront.CartPage(page).open().clear();
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("QA").warn("Не удалось очистить корзину после теста: {}", e.getMessage());
        }
    }
}
