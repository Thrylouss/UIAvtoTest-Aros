package uz.aros.autotests.reporting;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * «Чёрный ящик» одного теста: собирает всё, что понадобится, чтобы понять причину падения.
 *
 * <ul>
 *   <li>последние сетевые запросы страницы (fetch/XHR) и, отдельно, все ошибочные;</li>
 *   <li>ошибки в консоли браузера и необработанные JS-исключения;</li>
 *   <li>URL страницы и скриншот в момент падения.</li>
 * </ul>
 *
 * <p>Объект создаётся расширением {@link uz.aros.autotests.extensions.DiagnosticsExtension}
 * перед каждым тестом, а {@link #attach(Page)} вызывается, когда для теста открыта страница.
 */
public final class TestDiagnostics {

    /** Сколько последних «обычных» запросов помнить (ошибочные хранятся все). */
    private static final int RECENT_LIMIT = 25;
    private static final int BODY_LIMIT = 500;

    private final String testId;
    private final Deque<ApiCall> recent = new ArrayDeque<>();
    private final List<ApiCall> problems = new ArrayList<>();
    private final List<String> consoleErrors = new ArrayList<>();
    private final Set<Integer> ignoredStatuses = new HashSet<>();
    private final Map<Request, Long> startTimes = new IdentityHashMap<>();

    private volatile String lastUrl = "";
    private volatile String pageTitle = "";
    private volatile Path screenshot;
    private volatile String ownHostSuffix = "aros.uz";

    public TestDiagnostics(String testId) {
        this.testId = testId;
    }

    public String testId() {
        return testId;
    }

    /** Тест намеренно ждёт такой HTTP-статус (например, 404 на странице «не найдено») — не считать его проблемой. */
    public void ignoreStatus(int... statuses) {
        for (int s : statuses) ignoredStatuses.add(s);
    }

    /** Подписывается на события страницы. Вызывается один раз для каждой открытой страницы теста. */
    public void attach(Page page) {
        String base = uz.aros.autotests.config.EnvConfig.baseUrl();
        try {
            String host = URI.create(base).getHost();
            String[] parts = host.split("\\.");
            if (parts.length >= 2) ownHostSuffix = parts[parts.length - 2] + "." + parts[parts.length - 1];
        } catch (Exception ignored) {
            // оставляем значение по умолчанию
        }

        page.onRequest(req -> {
            synchronized (this) {
                startTimes.put(req, System.currentTimeMillis());
            }
        });
        page.onResponse(this::onResponse);
        page.onRequestFailed(req -> {
            String failure = req.failure() == null ? "" : req.failure();
            // ERR_ABORTED — обычный шум: браузер сам отменил запрос при переходе на другую страницу
            if (failure.contains("ERR_ABORTED") || !isOwnHost(req.url())) return;
            record(new ApiCall(req.method(), req.url(), 0, failure, elapsed(req), mask(req.postData()), "", req.resourceType()));
        });
        page.onConsoleMessage(msg -> {
            if ("error".equals(msg.type()) && isOwnHostOrInline(msg)) {
                synchronized (this) {
                    if (consoleErrors.size() < 30) consoleErrors.add(trim(msg.text(), 300));
                }
            }
        });
        page.onPageError(err -> {
            synchronized (this) {
                if (consoleErrors.size() < 30) consoleErrors.add("JS-исключение: " + trim(err, 300));
            }
        });
    }

    private void onResponse(Response res) {
        Request req = res.request();
        String type = req.resourceType();
        boolean interesting = "fetch".equals(type) || "xhr".equals(type) || "document".equals(type);
        if (!interesting || !isOwnHost(req.url())) return;

        int status = res.status();
        String body = "";
        if (status >= 400 && !ignoredStatuses.contains(status)) {
            try {
                body = trim(res.text(), BODY_LIMIT);
            } catch (Exception e) {
                body = "(тело ответа недоступно)";
            }
        }
        record(new ApiCall(req.method(), req.url(), status, res.statusText(), elapsed(req),
                mask(req.postData()), body, type));
    }

    private synchronized void record(ApiCall call) {
        if (call.isProblem() && !ignoredStatuses.contains(call.status())) {
            problems.add(call);
        }
        recent.addLast(call);
        while (recent.size() > RECENT_LIMIT) recent.removeFirst();
    }

    private long elapsed(Request req) {
        Long start;
        synchronized (this) {
            start = startTimes.remove(req);
        }
        return start == null ? -1 : System.currentTimeMillis() - start;
    }

    /**
     * Адреса, ошибки которых к тестируемому функционалу не относятся. Сейчас это «туннель» Sentry
     * ({@code /monitoring}): сайт отправляет в него свои ошибки, а Sentry на staging регулярно отвечает 429
     * (исчерпана квота). Это шум — он не должен попадать ни в отчёты, ни в «подозрительное».
     */
    private static boolean isNoise(String url) {
        return url != null && url.contains("/monitoring");
    }

    private boolean isOwnHost(String url) {
        if (isNoise(url)) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            return host != null && host.endsWith(ownHostSuffix);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isOwnHostOrInline(com.microsoft.playwright.ConsoleMessage msg) {
        String loc = msg.location();
        if (isNoise(loc)) {
            return false;
        }
        return loc == null || loc.isBlank() || loc.contains(ownHostSuffix);
    }

    /** Пароли и токены не должны попадать в отчёты, которые уходят в чат. */
    static String mask(String body) {
        if (body == null || body.isBlank()) return "";
        String masked = body.replaceAll("(?i)(\"(?:password|pass|token|access_token|refresh_token|code|otp)\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
        return trim(masked, 300);
    }

    static String trim(String s, int max) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > max ? one.substring(0, max) + "…" : one;
    }

    // ---- то, что заполняет расширение при падении ----
    public void setFailureContext(String url, String title, Path screenshotPath) {
        this.lastUrl = url == null ? "" : url;
        this.pageTitle = title == null ? "" : title;
        this.screenshot = screenshotPath;
    }

    // ---- геттеры для отчётов ----
    public synchronized List<ApiCall> problems() {
        return List.copyOf(problems);
    }

    public synchronized List<ApiCall> recentCalls() {
        return List.copyOf(recent);
    }

    public synchronized List<String> consoleErrors() {
        return List.copyOf(consoleErrors);
    }

    public String lastUrl() {
        return lastUrl;
    }

    public String pageTitle() {
        return pageTitle;
    }

    public Path screenshot() {
        return screenshot;
    }
}
