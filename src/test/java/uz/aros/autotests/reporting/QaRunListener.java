package uz.aros.autotests.reporting;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.aros.autotests.config.EnvConfig;
import uz.aros.autotests.telegram.TelegramClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Главный «репортёр» прогона. Подключается к JUnit автоматически (через файл
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}) и делает три вещи:
 *
 * <ol>
 *   <li>пишет в консоль понятный лог по ходу прогона: старт каждого теста, результат, причину падения и эндпоинты;</li>
 *   <li>в конце сохраняет отчёты: {@code target/qa-report/report.html} и {@code last-run.txt};</li>
 *   <li>если настроен Telegram — отправляет сводку, детали падений со скриншотами и HTML-файл в чат.</li>
 * </ol>
 */
public class QaRunListener implements TestExecutionListener {

    private static final Logger log = LoggerFactory.getLogger("QA");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern ID_PREFIX = Pattern.compile("^[A-Z0-9]{2,5}-X?\\d{2,3}\\s*[·:\\-–]\\s*");
    static final Path REPORT_DIR = Path.of("target", "qa-report");

    private final Map<String, Long> startedAt = new ConcurrentHashMap<>();
    private final List<TestRecord> records = new ArrayList<>();
    private Instant runStart;

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        runStart = Instant.now();
        log.info("""

                ════════════════════════════════════════════════════════════════
                  ▶ Aros UI · старт прогона
                  сайт: {}   язык: {}   запуск: {}
                ════════════════════════════════════════════════════════════════""",
                EnvConfig.baseUrl(), EnvConfig.locale(), EnvConfig.runLabel().isBlank() ? "mvn test" : EnvConfig.runLabel());
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (!id.isTest()) return;
        startedAt.put(id.getUniqueId(), System.currentTimeMillis());
        log.info("▶ {}", describe(id));
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (id.isContainer() && id.getParentId().isPresent() && id.getSource().isPresent() && !(id.getSource().get() instanceof MethodSource)) {
            // пропущен целый класс (@Disabled) — фиксируем одной записью
        } else if (!id.isTest()) {
            return;
        }
        add(new TestRecord(id.getUniqueId(), caseId(id), title(id), className(id), methodName(id), tags(id),
                TestRecord.Status.SKIPPED, 0, FailureAnalyzer.Reason.of(reason == null ? "пропущен" : reason), null));
        log.info("⏭ ПРОПУЩЕН  {}   ({})", describe(id), reason);
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        long took = startedAt.containsKey(id.getUniqueId()) ? System.currentTimeMillis() - startedAt.get(id.getUniqueId()) : 0;
        TestDiagnostics diag = DiagnosticsRegistry.forTest(id.getUniqueId());

        if (id.isTest()) {
            record(id, result, took, diag);
        } else if (result.getStatus() == TestExecutionResult.Status.FAILED && result.getThrowable().isPresent()) {
            // упал @BeforeAll/@AfterAll или инициализация класса — иначе это осталось бы незамеченным
            record(id, result, took, null);
        }
    }

    private void record(TestIdentifier id, TestExecutionResult result, long took, TestDiagnostics diag) {
        TestRecord.Status status;
        FailureAnalyzer.Reason reason = null;
        switch (result.getStatus()) {
            case SUCCESSFUL -> status = TestRecord.Status.PASSED;
            case ABORTED -> {
                status = TestRecord.Status.SKIPPED;
                reason = FailureAnalyzer.Reason.of(result.getThrowable().map(Throwable::getMessage).orElse("условие теста не выполнено (assumption)"));
            }
            default -> {
                status = TestRecord.Status.FAILED;
                reason = FailureAnalyzer.analyze(result.getThrowable().orElse(null));
            }
        }
        TestRecord rec = new TestRecord(id.getUniqueId(), caseId(id), title(id), className(id), methodName(id), tags(id),
                status, took, reason, diag);
        add(rec);
        logResult(rec);
    }

    private void logResult(TestRecord r) {
        String sec = String.format("%.1fс", r.durationMs / 1000.0);
        switch (r.status) {
            case PASSED -> {
                log.info("✔ ПРОШЁЛ    {}   ({})", r.label(), sec);
                if (r.diagnostics != null) for (ApiCall c : r.diagnostics.problems()) log.warn("   ⚠ эндпоинт ответил ошибкой: {}", c.oneLine());
            }
            case SKIPPED -> log.info("⏭ ПРОПУЩЕН  {}   ({})", r.label(), r.reason == null ? "" : r.reason.headline());
            case FAILED -> {
                StringBuilder sb = new StringBuilder();
                sb.append("✘ УПАЛ       ").append(r.label()).append("   (").append(sec).append(")\n");
                if (r.reason != null) {
                    sb.append("      причина : ").append(r.reason.headline()).append("\n");
                    if (!r.reason.detail().isBlank()) sb.append("      детали  : ").append(r.reason.detail()).append("\n");
                    if (!r.reason.element().isBlank()) sb.append("      элемент : ").append(r.reason.element()).append("\n");
                    if (!r.reason.location().isBlank()) sb.append("      в тесте : ").append(r.className).append(" (").append(r.reason.location()).append(")\n");
                }
                TestDiagnostics d = r.diagnostics;
                if (d != null) {
                    if (!d.lastUrl().isBlank()) sb.append("      страница: ").append(d.lastUrl()).append("\n");
                    if (d.problems().isEmpty()) {
                        sb.append("      эндпоинты: ошибок бэкенда не было\n");
                    } else {
                        sb.append("      ЭНДПОИНТЫ С ОШИБКАМИ:\n");
                        for (ApiCall c : d.problems()) {
                            sb.append("        • ").append(c.oneLine()).append("\n");
                            if (!c.requestBody().isBlank()) sb.append("            запрос:  ").append(c.requestBody()).append("\n");
                            if (!c.responseBody().isBlank()) sb.append("            ответ:   ").append(c.responseBody()).append("\n");
                        }
                    }
                    for (String e : d.consoleErrors()) sb.append("      консоль : ").append(e).append("\n");
                    if (d.screenshot() != null) sb.append("      скриншот: ").append(d.screenshot()).append("\n");
                }
                log.error(sb.toString().stripTrailing());
            }
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        List<TestRecord> snapshot;
        synchronized (records) {
            snapshot = List.copyOf(records);
        }
        if (snapshot.isEmpty()) return;

        RunReport report = new RunReport(EnvConfig.baseUrl(), EnvConfig.locale(), EnvConfig.runLabel(), EnvConfig.triggeredBy(),
                gitCommit(), runStart, System.currentTimeMillis() - runStart.toEpochMilli(), snapshot);
        printSummary(report);

        Path html = null;
        try {
            html = HtmlReport.write(report, REPORT_DIR);
            Files.writeString(REPORT_DIR.resolve("last-run.txt"), String.join("\n", TelegramFormatter.format(report))
                    .replaceAll("<[^>]+>", ""));
            writeResultsJson(snapshot);
            log.info("📄 HTML-отчёт: {}", html.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Не удалось сохранить отчёт: {}", e.getMessage());
        }
        sendToTelegram(report, html);
    }

    /**
     * Машиночитаемые результаты прогона: {@code target/qa-report/results.json}. Их читает скрипт
     * {@code tools/sync_testcases.py}, который переносит статусы в Excel с тест-кейсами.
     */
    private void writeResultsJson(List<TestRecord> snapshot) throws IOException {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (TestRecord t : snapshot) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("caseId", t.caseId);
            o.addProperty("title", t.title);
            o.addProperty("class", t.className);
            o.addProperty("method", t.methodName);
            o.addProperty("status", t.status.name());
            o.addProperty("reason", t.reason == null ? "" : t.reason.headline());
            com.google.gson.JsonArray tags = new com.google.gson.JsonArray();
            t.tags.forEach(tags::add);
            o.add("tags", tags);
            array.add(o);
        }
        Files.writeString(REPORT_DIR.resolve("results.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(array));
    }

    private void printSummary(RunReport r) {
        StringBuilder sb = new StringBuilder("\n════════════════════════════════════════════════════════════════\n");
        sb.append(r.allGreen() ? "  ✅ ИТОГ: всё зелёное\n" : "  🔴 ИТОГ: есть падения\n");
        sb.append(String.format("  прошло %d · упало %d · пропущено %d · всего %d · %d%% · %s%n",
                r.count(TestRecord.Status.PASSED), r.count(TestRecord.Status.FAILED), r.count(TestRecord.Status.SKIPPED),
                r.total(), r.passRatePercent(), TelegramFormatter.duration(r.durationMs)));
        for (Map.Entry<String, int[]> e : r.byModule().entrySet()) {
            int[] v = e.getValue();
            sb.append(String.format("   %s %-34s %d/%d%n", v[1] > 0 ? "✘" : "✔", e.getKey(), v[0], v[0] + v[1] + v[2]));
        }
        if (!r.failed().isEmpty()) {
            sb.append("  Упавшие:\n");
            for (TestRecord t : r.failed()) {
                sb.append("   ✘ ").append(t.label()).append("  —  ").append(t.reason == null ? "" : t.reason.headline()).append("\n");
            }
        }
        sb.append("════════════════════════════════════════════════════════════════");
        log.info(sb.toString());
    }

    private void sendToTelegram(RunReport report, Path html) {
        if (!EnvConfig.telegramEnabled()) {
            if (!EnvConfig.telegramToken().isBlank() || !EnvConfig.telegramChatIds().isEmpty()) {
                log.info("Telegram: отправка отключена (QA_NO_TELEGRAM=true или не задан токен/чат).");
            } else {
                log.info("Telegram не настроен — отчёт в чат не отправляется (см. README: «Telegram-бот»).");
            }
            return;
        }
        TelegramClient tg = new TelegramClient(EnvConfig.telegramToken());
        List<String> messages = TelegramFormatter.format(report);
        for (String chat : EnvConfig.telegramChatIds()) {
            boolean ok = true;
            for (String m : messages) ok &= tg.sendMessage(chat, m);
            int photos = 0;
            for (TestRecord t : report.failed()) {
                if (photos >= 5) break;                       // не заваливаем чат картинками
                Path shot = t.diagnostics == null ? null : t.diagnostics.screenshot();
                if (shot != null && Files.exists(shot)) {
                    tg.sendPhoto(chat, shot, "<b>" + (t.caseId == null ? "" : t.caseId + " · ") + esc(t.title) + "</b>");
                    photos++;
                }
            }
            if (html != null) tg.sendDocument(chat, html, "📄 Полный отчёт (откройте в браузере)");
            log.info(ok ? "📨 Отчёт отправлен в Telegram (chat {})" : "⚠ Не все части отчёта дошли до Telegram (chat {})", chat);
        }
    }

    // ---------------------------------------------------------------- помощники

    private void add(TestRecord r) {
        synchronized (records) {
            records.add(r);
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String describe(TestIdentifier id) {
        String cid = caseId(id);
        return (cid == null ? "" : cid + " · ") + title(id);
    }

    private static String title(TestIdentifier id) {
        return ID_PREFIX.matcher(id.getDisplayName()).replaceFirst("");
    }

    private static String caseId(TestIdentifier id) {
        return id.getTags().stream().map(t -> ModuleNames.asCaseId(t.getName())).filter(x -> x != null).findFirst().orElse(null);
    }

    private static List<String> tags(TestIdentifier id) {
        return id.getTags().stream().map(t -> t.getName()).sorted().toList();
    }

    private static String className(TestIdentifier id) {
        return id.getSource().filter(s -> s instanceof MethodSource).map(s -> ((MethodSource) s).getClassName())
                .orElse(id.getLegacyReportingName());
    }

    private static String methodName(TestIdentifier id) {
        return id.getSource().filter(s -> s instanceof MethodSource).map(s -> ((MethodSource) s).getMethodName()).orElse("");
    }

    private static String gitCommit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            return p.waitFor() == 0 ? out : "";
        } catch (Exception e) {
            return "";
        }
    }
}
