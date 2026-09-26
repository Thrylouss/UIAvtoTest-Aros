package uz.aros.autotests.extensions;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.aros.autotests.base.BaseUiTest;

import java.io.ByteArrayInputStream;

/**
 * Автоматически прикладывает скриншот страницы к Allure-отчёту, когда
 * тестовый метод падает — экономит время при разборе падений в CI: не
 * нужно гадать, что было на экране, скриншот уже лежит в отчёте.
 *
 * <h2>Почему {@link TestExecutionExceptionHandler}, а не {@code TestWatcher}</h2>
 * Первая версия этого класса использовала {@code TestWatcher.testFailed(...)}
 * — интуитивно кажется, что это правильное место для реакции на падение
 * теста. На практике же JUnit5 вызывает {@code TestWatcher} самым
 * ПОСЛЕДНИМ, уже после того как отработают все {@code @AfterEach} —
 * включая {@code BaseUiTest.closeContext()}, который закрывает страницу.
 * К моменту вызова {@code testFailed(...)} браузерная страница уже закрыта,
 * и сделать скриншот невозможно (тихо падает в try/catch — поэтому
 * скриншоты и не появлялись в первой версии).
 *
 * <p>{@link TestExecutionExceptionHandler}, наоборот, перехватывает
 * исключение СРАЗУ там, где оно возникло — до того, как отработает хотя бы
 * один {@code @AfterEach}. Страница в этот момент ещё открыта и показывает
 * ровно то состояние, при котором тест упал. Обработчик обязан либо
 * подавить исключение, либо перебросить его дальше — здесь всегда
 * перебрасываем, чтобы тест по-прежнему помечался как упавший.
 */
public class ScreenshotOnFailureExtension implements TestExecutionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotOnFailureExtension.class);

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        context.getTestInstance()
                .filter(instance -> instance instanceof BaseUiTest)
                .map(instance -> ((BaseUiTest) instance).getPage())
                .ifPresent(page -> attachScreenshot(page, context.getDisplayName()));

        // Обязательно пробрасываем исключение дальше: если этого не сделать,
        // JUnit5 решит, что мы "обработали" ошибку, и тест станет зелёным.
        throw throwable;
    }

    private void attachScreenshot(Page page, String testDisplayName) {
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment(
                    "Скриншот в момент падения: " + testDisplayName,
                    "image/png",
                    new ByteArrayInputStream(screenshot),
                    "png"
            );
        } catch (Exception e) {
            log.warn("Не удалось сделать скриншот после падения теста '{}': {}", testDisplayName, e.getMessage());
        }
    }
}
