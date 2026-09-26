package uz.aros.autotests.pages.storefront.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Step;
import uz.aros.autotests.base.BasePage;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Page Object формы входа (модальное окно "Sign in").
 *
 * <p>Форма умеет работать в двух режимах, которые переключаются кнопкой
 * внутри неё же:
 * <ul>
 *   <li>вход по одноразовому коду на телефон (режим по умолчанию);</li>
 *   <li>вход по паролю — появляется поле пароля (этот режим и тестируем).</li>
 * </ul>
 *
 * <h2>Почему локаторы именно такие</h2>
 * Сайт построен на MUI, и большинство CSS-классов на странице — это хэши
 * вида {@code mui-1a2b3c}, которые пересобираются при каждом деплое
 * фронтенда. Использовать их как селекторы нельзя — тесты сломаются при
 * следующем же релизе без единого изменения в интерфейсе. Поэтому здесь
 * везде используются куда более стабильные вещи:
 * <ul>
 *   <li>{@code getByRole(...)} — поиск по роли элемента (кнопка, поле ввода)
 *       и видимому тексту/aria-label. Это то же самое, что видит и на что
 *       ориентируется screen reader — такой API одновременно и стабилен,
 *       и делает тест немного проверкой доступности сайта "бесплатно";</li>
 *   <li>стабильные HTML-атрибуты вроде {@code name="password"} — не
 *       зависят от CSS-фреймворка (в отличие от {@code type}, который
 *       переключатель видимости пароля меняет на лету — см. комментарий
 *       у поля {@link #passwordInput}).</li>
 * </ul>
 */
public class SignInForm extends BasePage {

    /** Корень формы — все локаторы ниже ищутся ВНУТРИ него (through .locator() on this root),
     *  чтобы случайно не зацепить одноимённую кнопку "Sign in" в шапке сайта. */
    private final Locator root;

    private final Locator heading;
    private final Locator phoneInput;
    private final Locator passwordInput;
    private final Locator passwordVisibilityToggle;
    private final Locator switchToPasswordButton;
    private final Locator switchToPhoneButton;
    private final Locator forgotPasswordButton;
    private final Locator signUpButton;
    private final Locator submitButton;

    public SignInForm(Page page) {
        super(page);

        this.root = page.locator("form")
                .filter(new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sign in"))))
                .first();

        this.heading = root.getByRole(AriaRole.HEADING, new Locator.GetByRoleOptions().setName("Sign in"));
        this.phoneInput = root.locator("input[type=\"text\"]").first();
        // ВАЖНО: локатор именно по атрибуту name, а НЕ type="password".
        // Кнопка-"глазик" переключает атрибут type этого же поля между
        // password/text — локатор по type="password" перестал бы находить
        // элемент сразу после того, как пользователь показал пароль.
        // name="password" не меняется никогда, поэтому локатор остаётся
        // рабочим в обоих состояниях поля.
        this.passwordInput = root.locator("input[name=\"password\"]");
        this.passwordVisibilityToggle = root.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("toggle password visibility"));
        this.switchToPasswordButton = root.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Sign in with password"));
        this.switchToPhoneButton = root.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Entry by phone number"));
        this.forgotPasswordButton = root.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Forgot password?"));
        this.signUpButton = root.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Sign up"));
        this.submitButton = root.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName("Sign in").setExact(true));
    }

    /** Дожидается, пока модальное окно реально отрисуется — вызывать сразу после открытия формы. */
    @Step("Дождаться появления формы входа")
    public SignInForm waitUntilVisible() {
        heading.waitFor();
        return this;
    }

    /** true, если сейчас показан режим "вход по паролю" (видно поле пароля). */
    public boolean isPasswordMode() {
        return passwordInput.count() > 0 && passwordInput.isVisible();
    }

    /** Переключает форму в режим "вход по паролю", если она ещё не в нём. */
    @Step("Переключиться в режим входа по паролю")
    public SignInForm switchToPasswordLogin() {
        if (!isPasswordMode()) {
            switchToPasswordButton.click();
            passwordInput.waitFor();
        }
        return this;
    }

    /** Переключает форму обратно в режим "вход по номеру телефона". */
    @Step("Переключиться в режим входа по номеру телефона")
    public SignInForm switchToPhoneLogin() {
        if (isPasswordMode()) {
            switchToPhoneButton.click();
            passwordInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
        }
        return this;
    }

    /**
     * Заполняет номер телефона.
     *
     * <p>Поле телефона — маскированный инпут (постоянный префикс {@code +998}
     * плюс автоформатирование "(99) 898-78-82" прямо во время ввода).
     * Первая версия этого метода печатала текст посимвольно через
     * {@link Locator#pressSequentially}, как это делает живой человек —
     * стандартная рекомендация для масок. На практике на ЭТОЙ конкретной
     * маске это регулярно приводило к порче номера: маска переформатирует
     * своё значение (вставляет пробелы/скобки/дефисы) после КАЖДОГО
     * нажатия, и programmatic-нажатия от Playwright оказываются либо
     * слишком быстрыми, либо просто не синхронизированными с этим
     * переформатированием — часть цифр в итоге попадала не на свои места
     * (было обнаружено эмпирически, живым прогоном: тест падал с ошибкой
     * "User does not exist" при абсолютно правильном номере и пароле).
     *
     * <p>Метод {@link Locator#fill}, наоборот, устанавливает значение поля
     * ОДНИМ действием и один раз вызывает обработчик реакта на изменение —
     * маска форматирует уже готовую строку целиком, без промежуточных
     * состояний, и на практике оказалась надёжнее для этого конкретного
     * компонента.
     *
     * <p>Метод принимает пустую строку осознанно (для негативных тестов
     * вроде "пустой номер телефона") — в этом случае просто очищаем поле.
     *
     * <p><b>Про ведущий "+":</b> маска сама показывает "+998" как визуальный
     * префикс, а ожидает от вас только цифры. Поэтому один ведущий "+",
     * если он есть во входной строке, обрезается перед заполнением — иначе
     * он занимает место одной из значащих цифр и сдвигает весь номер.
     * Остальные некорректные символы (например, буквы в тестовых кейсах на
     * невалидный формат) НЕ трогаем — их и должна отбраковать сама маска.
     */
    @Step("Ввести номер телефона: {phone}")
    public SignInForm fillPhone(String phone) {
        String withoutLeadingPlus = (phone != null && phone.startsWith("+")) ? phone.substring(1) : phone;
        phoneInput.fill(withoutLeadingPlus == null ? "" : withoutLeadingPlus);
        return this;
    }

    @Step("Ввести пароль")
    public SignInForm fillPassword(String password) {
        passwordInput.fill(password);
        return this;
    }

    @Step("Переключить видимость пароля")
    public SignInForm togglePasswordVisibility() {
        passwordVisibilityToggle.click();
        return this;
    }

    @Step("Нажать кнопку отправки формы")
    public void submit() {
        submitButton.click();
    }

    /** Полный сценарий: переключиться на вход по паролю, заполнить оба поля и отправить форму. */
    @Step("Войти по телефону и паролю")
    public void signInWithPassword(String phone, String password) {
        switchToPasswordLogin();
        fillPhone(phone);
        fillPassword(password);
        submit();
    }

    public Locator passwordInput() {
        return passwordInput;
    }

    public Locator forgotPasswordButton() {
        return forgotPasswordButton;
    }

    public Locator signUpButton() {
        return signUpButton;
    }

    /** Утверждение "пароль сейчас скрыт" (type=password) — удобно для тестов на UI-поведение переключателя видимости. */
    public void assertPasswordIsMasked() {
        assertThat(passwordInput).hasAttribute("type", "password");
    }

    /** Утверждение "пароль сейчас показан в открытом виде" (type=text). */
    public void assertPasswordIsRevealed() {
        assertThat(passwordInput).hasAttribute("type", "text");
    }
}
