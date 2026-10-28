package uz.aros.autotests.tools;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import uz.aros.autotests.config.EnvConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * «Зонд» страниц — инструмент разработчика автотестов (в самих тестах не используется).
 *
 * <p>Он открывает сайт, при необходимости логинится и выполняет простой сценарий из текстового файла, а затем
 * печатает дерево элементов страницы (accessibility snapshot) — именно по нему удобно подбирать локаторы
 * ({@code getByRole(...)}), не гадая и не листая DevTools.
 *
 * <p>Запуск: <pre>mvn -q test-compile exec:java@probe -Dexec.args="--login --file probe.txt"</pre>
 *
 * <p>Опции: {@code --login} (войти тестовым пользователем), {@code --mobile} (viewport телефона),
 * {@code --headed} (показать браузер), {@code --state target/auth/user.json} (стартовать с сохранённой сессией, без входа), {@code --max 6000} (макс. символов на один вывод), {@code --file путь}.
 *
 * <p>Файл сценария — по одной команде на строку (строки с # игнорируются):
 * <pre>
 * goto:/ru/cart                  открыть страницу (путь от корня сайта или полный URL)
 * click:text=Вход                клик по Playwright-селектору
 * fill:input[name=password]=abc  ввести текст: селектор=значение
 * press:input[name=q]=Enter      нажать клавишу в элементе: селектор=клавиша
 * text:body                      видимый текст (innerText) элемента
 * wait:1500                      пауза, мс
 * snap:main                      дерево элементов для CSS-селектора ("-" — вся страница)
 * eval:document.title            выполнить JS и напечатать результат
 * shot:cart                      скриншот в target/probe/cart.png
 * </pre>
 */
public final class PageProbe {

    private PageProbe() {
    }

    public static void main(String[] args) throws Exception {
        boolean login = false, mobile = false, headed = false;
        String state = null;
        int max = 6000;
        String file = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--login" -> login = true;
                case "--mobile" -> mobile = true;
                case "--headed" -> headed = true;
                case "--max" -> max = Integer.parseInt(args[++i]);
                case "--file" -> file = args[++i];
                case "--state" -> state = args[++i];
                default -> throw new IllegalArgumentException("Неизвестная опция: " + args[i]);
            }
        }
        if (file == null) throw new IllegalArgumentException("Укажите --file со сценарием (см. javadoc PageProbe)");
        List<String> steps = Files.readAllLines(Path.of(file));
        Files.createDirectories(Path.of("target", "probe"));

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(!headed));
            Browser.NewContextOptions opts = new Browser.NewContextOptions().setLocale("ru-RU");
            if (mobile) opts.setViewportSize(390, 844).setIsMobile(true).setHasTouch(true); else opts.setViewportSize(1440, 900);
            if (state != null) opts.setStorageStatePath(Path.of(state));
            BrowserContext ctx = browser.newContext(opts);
            ctx.setDefaultTimeout(10_000);
            Page page = ctx.newPage();
            page.onResponse(r -> {
                if (r.status() >= 400 && r.url().contains("aros.uz") && !"image".equals(r.request().resourceType()))
                    System.out.println("   [http " + r.status() + "] " + r.request().method() + " " + r.url());
            });

            if (login) doLogin(page);

            for (String raw : steps) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int c = line.indexOf(':');
                String cmd = c < 0 ? line : line.substring(0, c);
                String val = c < 0 ? "" : line.substring(c + 1);
                System.out.println("\n>>> " + line);
                try {
                    switch (cmd) {
                        case "goto" -> {
                            page.navigate(val.startsWith("http") ? val : EnvConfig.siteRoot() + val);
                            settle(page);
                            System.out.println("    url=" + page.url() + "  title=" + page.title());
                        }
                        case "click" -> {
                            page.locator(val).first().click();
                            settle(page);
                        }
                        case "fill" -> {
                            int eq = val.lastIndexOf('=');
                            page.locator(val.substring(0, eq)).first().fill(val.substring(eq + 1));
                        }
                        case "press" -> {
                            int eq = val.lastIndexOf('=');
                            page.locator(val.substring(0, eq)).first().press(val.substring(eq + 1));
                            settle(page);
                            System.out.println("    url=" + page.url());
                        }
                        case "text" -> print(page.locator(val.strip()).first().innerText(), max);
                        case "wait" -> page.waitForTimeout(Integer.parseInt(val.strip()));
                        case "snap" -> {
                            Locator l = "-".equals(val.strip()) ? page.locator("body") : page.locator(val.strip()).first();
                            print(l.ariaSnapshot(), max);
                        }
                        case "eval" -> print(String.valueOf(page.evaluate(val)), max);
                        case "shot" -> {
                            Path p = Path.of("target", "probe", val.strip() + ".png");
                            page.screenshot(new Page.ScreenshotOptions().setPath(p));
                            System.out.println("    сохранён " + p);
                        }
                        default -> System.out.println("    (неизвестная команда)");
                    }
                } catch (Exception e) {
                    System.out.println("    !! " + e.getMessage().split("\n")[0]);
                }
            }
            browser.close();
        }
    }

    /** Вход без привязки к языку: ищем кнопку «Вход/Sign in/Kirish» в шапке, потом режим «по паролю». */
    private static void doLogin(Page page) {
        page.navigate(EnvConfig.baseUrl());
        settle(page);
        // В шапке кнопка входа — последняя кнопка (справа); у залогиненного пользователя будет профиль.
        page.getByRole(com.microsoft.playwright.options.AriaRole.BANNER).getByRole(com.microsoft.playwright.options.AriaRole.BUTTON).last().click();
        page.locator("form").first().waitFor();
        Locator pass = page.locator("form input[name=password]");
        if (pass.count() == 0) {
            page.locator("form button.MuiButton-outlined").first().click();
            pass.first().waitFor();
        }
        page.locator("form input[type=text]").first().fill(EnvConfig.testUserPhone().replaceFirst("^\\+", ""));
        pass.first().fill(EnvConfig.testUserPassword());
        page.locator("form button[type=submit]").click();
        page.waitForTimeout(2500);
        System.out.println("[probe] вход выполнен, url=" + page.url());
    }

    private static void settle(Page page) {
        page.waitForLoadState();
        page.waitForTimeout(1200);
    }

    private static void print(String text, int max) {
        String t = text == null ? "" : text;
        System.out.println(t.length() > max ? t.substring(0, max) + "\n    …(обрезано, всего " + t.length() + " символов; увеличьте --max)" : t);
    }
}
