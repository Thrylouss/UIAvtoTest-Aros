package uz.aros.autotests.base;

import com.microsoft.playwright.Page;
import uz.aros.autotests.playwrightsupport.AuthSession;
import uz.aros.autotests.playwrightsupport.PlaywrightManager;

/**
 * База для мобильных тестов: эмулируем телефон (390×844, touch). Гость или вошедший — выбирается
 * методом {@link #loggedIn()}.
 */
public abstract class MobileUiTest extends BaseUiTest {

    /** Переопределите и верните true, если мобильному тесту нужна авторизация. */
    protected boolean loggedIn() {
        return false;
    }

    @Override
    protected Page createPage() {
        return PlaywrightManager.newPage(loggedIn() ? AuthSession.userState() : null, true);
    }
}
