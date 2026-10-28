package uz.aros.autotests.extensions;

import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.aros.autotests.base.BaseUiTest;
import uz.aros.autotests.reporting.DiagnosticsRegistry;
import uz.aros.autotests.reporting.TestDiagnostics;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Следит за каждым UI-тестом и собирает «улики» на случай падения.
 *
 * <ol>
 *   <li><b>Перед тестом</b> — заводит {@link TestDiagnostics}: дальше {@code PlaywrightManager}
 *       подписывает на неё страницу и она записывает сетевые запросы и ошибки консоли.</li>
 *   <li><b>При падении</b> (в самом тесте или в {@code @BeforeEach}) — пока страница ещё открыта,
 *       делает скриншот, запоминает URL и заголовок. Скриншот попадает и в Allure, и в файл
 *       {@code target/qa-report/screenshots}, откуда его берёт Telegram-отчёт.</li>
 * </ol>
 *
 * <p>Почему именно {@link TestExecutionExceptionHandler}, а не {@code TestWatcher}: TestWatcher
 * вызывается ПОСЛЕ {@code @AfterEach}, когда страница уже закрыта и скриншот сделать нельзя.
 * Обработчик исключения срабатывает сразу, пока браузер ещё жив.
 */
public class DiagnosticsExtension implements BeforeEachCallback, AfterEachCallback,
        TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsExtension.class);
    public static final Path SCREENSHOT_DIR = Path.of("target", "qa-report", "screenshots");

    @Override
    public void beforeEach(ExtensionContext context) {
        DiagnosticsRegistry.begin(context.getUniqueId());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        DiagnosticsRegistry.endCurrent();
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        capture(context);
        throw throwable;
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        capture(context);
        throw throwable;
    }

    private void capture(ExtensionContext context) {
        TestDiagnostics diagnostics = DiagnosticsRegistry.forTest(context.getUniqueId());
        Page page = context.getTestInstance()
                .filter(instance -> instance instanceof BaseUiTest)
                .map(instance -> ((BaseUiTest) instance).getPage())
                .orElse(null);
        if (diagnostics == null || page == null) return;

        try {
            Files.createDirectories(SCREENSHOT_DIR);
            Path file = SCREENSHOT_DIR.resolve(safeName(context.getUniqueId()) + ".png");
            // Скриншот ТОЛЬКО видимой области: Telegram отвергает слишком «высокие» картинки.
            byte[] viewport = page.screenshot(new Page.ScreenshotOptions().setPath(file));
            diagnostics.setFailureContext(page.url(), page.title(), file);
            Allure.addAttachment("Скриншот при падении: " + context.getDisplayName(),
                    "image/png", new ByteArrayInputStream(viewport), "png");
            // В Allure дополнительно прикладываем полную страницу — удобно листать вверх-вниз
            byte[] full = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.addAttachment("Вся страница", "image/png", new ByteArrayInputStream(full), "png");
        } catch (Exception e) {
            log.warn("Не удалось сделать скриншот после падения '{}': {}", context.getDisplayName(), e.getMessage());
        }
    }

    private static String safeName(String uniqueId) {
        // uniqueId выглядит как [engine:junit-jupiter]/[class:...]/[method:...] — берём последний сегмент
        // и добавляем хэш, чтобы параметризованные запуски одного метода не затирали файлы друг друга
        String tail = uniqueId.substring(uniqueId.lastIndexOf('/') + 1);
        return (tail + "-" + Integer.toHexString(uniqueId.hashCode())).replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
