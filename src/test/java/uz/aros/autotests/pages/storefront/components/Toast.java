package uz.aros.autotests.pages.storefront.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Обёртка над всплывающими уведомлениями (toast), которые сайт показывает
 * после действий вроде входа/выхода.
 *
 * <p>Сайт использует библиотеку react-toastify. Почему это важно для
 * локаторов: большая часть CSS-классов на этом сайте сгенерирована
 * MUI (material-ui) и выглядит как {@code mui-1a2b3c4} — такие классы
 * пересобираются заново при каждой сборке фронтенда, и селектор на их
 * основе развалится после первого же деплоя без единого изменения в UI.
 *
 * <p>А вот классы react-toastify — {@code Toastify__toast--success} /
 * {@code Toastify__toast--error} — это классы САМОЙ библиотеки уведомлений,
 * они захардкожены в её исходном коде и не меняются от сборки к сборке.
 * Поэтому их безопасно использовать как локаторы.
 */
public class Toast {

    private static final String SUCCESS_SELECTOR = ".Toastify__toast--success";
    private static final String ERROR_SELECTOR = ".Toastify__toast--error";

    private final Page page;

    public Toast(Page page) {
        this.page = page;
    }

    /**
     * Проверяет, что появился toast об успехе, и (опционально) что в нём
     * есть ожидаемый текст.
     *
     * <p>{@code assertThat(locator)...} — это "web-first assertion" из
     * Playwright: в отличие от обычного {@code assertEquals}, она не
     * проверяет состояние один раз и сразу, а повторяет проверку в течение
     * таймаута, пока условие не станет истинным (или пока не кончится время).
     * Это устраняет самый частый источник "флакающих" (нестабильных) UI-тестов —
     * гонку между тестом и асинхронной отрисовкой на странице.
     */
    @Step("Проверить toast об успешном действии")
    public Locator expectSuccess(String expectedTextOrNull) {
        Locator toast = page.locator(SUCCESS_SELECTOR).first();
        assertThat(toast).isVisible();
        if (expectedTextOrNull != null) {
            assertThat(toast).containsText(expectedTextOrNull);
        }
        return toast;
    }

    /** Проверяет, что появился toast с ошибкой (без проверки конкретного текста). */
    @Step("Проверить toast об ошибке")
    public Locator expectError() {
        Locator toast = page.locator(ERROR_SELECTOR).first();
        assertThat(toast).isVisible();
        return toast;
    }

    /**
     * Проверяет, что toast об успехе НЕ появился — основная проверка для
     * негативных тестов логина (неверный пароль и т.п.): нам не так важен
     * точный текст ошибки, как то, что операция точно не считается успешной.
     *
     * <p>Почему здесь есть фиксированная пауза, а не {@code waitForLoadState}:
     * на странице постоянно "живёт" виджет чата (см. пузырёк в углу сайта),
     * который держит фоновое сетевое соединение открытым. Из-за этого
     * {@code NETWORKIDLE} ("сеть затихла") на этом сайте практически никогда
     * не наступает и тест просто зависает на таймауте — это было обнаружено
     * именно так, при живом прогоне.
     *
     * <p>А "проверить, что чего-то НЕТ" — в принципе особый случай для
     * Playwright: retrying-проверка ({@code assertThat(...).hasCount(0)})
     * умеет ждать, пока условие СТАНЕТ истинным, но если оно истинно уже
     * в момент вызова (мы ещё не дали странице времени отреагировать на
     * клик), проверка пройдёт мгновенно и ничего не докажет. Поэтому даём
     * странице разумный тайм-аут на то, чтобы её JS успел отреагировать на
     * отправку формы, и только потом проверяем итоговое состояние.
     */
    @Step("Проверить, что toast об успехе НЕ появился")
    public void expectNoSuccess() {
        page.waitForTimeout(1_500);
        assertThat(page.locator(SUCCESS_SELECTOR)).hasCount(0);
    }
}
