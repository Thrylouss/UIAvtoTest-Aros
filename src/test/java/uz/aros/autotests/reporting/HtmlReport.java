package uz.aros.autotests.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

/**
 * Самодостаточный HTML-отчёт (один файл, скриншоты внутри в base64) — удобно открыть в браузере
 * или получить документом в Telegram.
 */
public final class HtmlReport {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private HtmlReport() {
    }

    public static Path write(RunReport r, Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("report.html");
        Files.writeString(file, render(r));
        return file;
    }

    static String render(RunReport r) {
        StringBuilder h = new StringBuilder(32_000);
        h.append("<!doctype html><html lang='ru'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
                .append("<title>Aros UI — отчёт</title><style>").append(CSS).append("</style></head><body>");

        String cls = r.allGreen() ? "ok" : "bad";
        h.append("<header class='").append(cls).append("'><h1>").append(r.allGreen() ? "✅ Прогон успешен" : "🔴 Есть падения").append("</h1>")
                .append("<p>").append(esc(r.baseUrl)).append(" · ").append(TIME.format(r.startedAt)).append(" · ")
                .append(TelegramFormatter.duration(r.durationMs));
        if (!r.label.isBlank()) h.append(" · запуск <code>").append(esc(r.label)).append("</code>");
        if (!r.triggeredBy.isBlank()) h.append(" · ").append(esc(r.triggeredBy));
        if (!r.commit.isBlank()) h.append(" · commit <code>").append(esc(r.commit)).append("</code>");
        h.append("</p></header><main>");

        // карточки
        h.append("<section class='cards'>")
                .append(card("Пройдено", r.count(TestRecord.Status.PASSED), "pass"))
                .append(card("Упало", r.count(TestRecord.Status.FAILED), "fail"))
                .append(card("Пропущено", r.count(TestRecord.Status.SKIPPED), "skip"))
                .append(card("Всего", r.total(), "all"))
                .append("</section>");
        h.append("<div class='progress'><div style='width:").append(r.passRatePercent()).append("%'></div></div>")
                .append("<p class='muted'>Успешность: <b>").append(r.passRatePercent()).append("%</b></p>");

        // модули
        h.append("<h2>По модулям</h2><table><tr><th>Модуль</th><th>Прошло</th><th>Упало</th><th>Пропущено</th></tr>");
        for (Map.Entry<String, int[]> e : r.byModule().entrySet()) {
            int[] v = e.getValue();
            h.append("<tr><td>").append(esc(e.getKey())).append("</td><td class='p'>").append(v[0]).append("</td><td class='f'>")
                    .append(v[1]).append("</td><td class='s'>").append(v[2]).append("</td></tr>");
        }
        h.append("</table>");

        // упавшие
        if (!r.failed().isEmpty()) {
            h.append("<h2>❌ Упавшие тесты</h2>");
            for (TestRecord t : r.failed()) h.append(failureCard(t));
        }
        if (!r.passedWithWarnings().isEmpty()) {
            h.append("<h2>⚠️ Прошли, но есть подозрительное</h2>");
            for (TestRecord t : r.passedWithWarnings()) {
                h.append("<div class='card warn'><b>").append(esc(t.label())).append("</b>");
                h.append(apiTable(t.diagnostics, true)).append(consoleBlock(t.diagnostics)).append("</div>");
            }
        }

        // все тесты
        h.append("<h2>Все тесты</h2><table class='all'><tr><th>Статус</th><th>ID</th><th>Название</th><th>Модуль</th><th>Время</th></tr>");
        for (TestRecord t : r.records) {
            h.append("<tr class='").append(t.status.name().toLowerCase()).append("'><td>").append(icon(t.status)).append("</td><td><code>")
                    .append(esc(t.caseId == null ? "" : t.caseId)).append("</code></td><td>").append(esc(t.title)).append("</td><td>")
                    .append(esc(t.module)).append("</td><td>").append(t.durationMs / 1000.0).append(" с</td></tr>");
        }
        h.append("</table></main></body></html>");
        return h.toString();
    }

    private static String failureCard(TestRecord t) {
        StringBuilder h = new StringBuilder("<div class='card fail'>");
        h.append("<h3>").append(t.caseId == null ? "" : "<code>" + esc(t.caseId) + "</code> ").append(esc(t.title)).append("</h3>");
        FailureAnalyzer.Reason why = t.reason;
        if (why != null) {
            h.append("<p class='why'>📍 ").append(esc(why.headline())).append("</p>");
            if (!why.detail().isBlank()) h.append("<pre>").append(esc(why.detail())).append("</pre>");
            if (!why.element().isBlank()) h.append("<p>🔎 Элемент: <code>").append(esc(why.element())).append("</code></p>");
            if (!why.location().isBlank()) h.append("<p class='muted'>📄 ").append(esc(t.className)).append(" · ").append(esc(why.location())).append("</p>");
        }
        TestDiagnostics d = t.diagnostics;
        if (d != null) {
            if (!d.lastUrl().isBlank()) h.append("<p>🔗 <a href='").append(esc(d.lastUrl())).append("'>").append(esc(d.lastUrl())).append("</a></p>");
            h.append(apiTable(d, false)).append(consoleBlock(d));
            Path shot = d.screenshot();
            if (shot != null && Files.exists(shot)) {
                try {
                    String b64 = Base64.getEncoder().encodeToString(Files.readAllBytes(shot));
                    h.append("<img alt='скриншот' src='data:image/png;base64,").append(b64).append("'>");
                } catch (IOException ignored) {
                    // без скриншота
                }
            }
        }
        return h.append("</div>").toString();
    }

    private static String apiTable(TestDiagnostics d, boolean onlyProblems) {
        var calls = onlyProblems ? d.problems() : (d.problems().isEmpty() ? d.recentCalls() : d.problems());
        if (calls.isEmpty()) return "";
        StringBuilder h = new StringBuilder("<h4>🛰 ").append(d.problems().isEmpty() ? "Последние запросы" : "Ошибки эндпоинтов").append("</h4><table class='api'>")
                .append("<tr><th>Метод</th><th>Эндпоинт</th><th>Статус</th><th>Время</th><th>Тело запроса / ответа</th></tr>");
        for (ApiCall c : calls) {
            h.append("<tr class='").append(c.isProblem() ? "pr" : "").append("'><td>").append(esc(c.method())).append("</td><td>")
                    .append(esc(c.url())).append("</td><td>").append(c.status() == 0 ? "обрыв" : c.status()).append(" ")
                    .append(esc(c.statusText() == null ? "" : c.statusText())).append("</td><td>").append(c.durationMs() < 0 ? "" : c.durationMs() + " мс").append("</td><td>");
            if (!c.requestBody().isBlank()) h.append("➡ <code>").append(esc(c.requestBody())).append("</code><br>");
            if (!c.responseBody().isBlank()) h.append("⬅ <code>").append(esc(c.responseBody())).append("</code>");
            h.append("</td></tr>");
        }
        return h.append("</table>").toString();
    }

    private static String consoleBlock(TestDiagnostics d) {
        if (d.consoleErrors().isEmpty()) return "";
        StringBuilder h = new StringBuilder("<h4>🖥 Ошибки в консоли браузера</h4><ul>");
        for (String e : d.consoleErrors()) h.append("<li><code>").append(esc(e)).append("</code></li>");
        return h.append("</ul>").toString();
    }

    private static String card(String title, long n, String cls) {
        return "<div class='c " + cls + "'><span>" + n + "</span>" + title + "</div>";
    }

    private static String icon(TestRecord.Status s) {
        return switch (s) {
            case PASSED -> "✅";
            case FAILED -> "❌";
            case SKIPPED -> "⏭";
        };
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    private static final String CSS = """
            *{box-sizing:border-box}body{margin:0;font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;background:#f5f6f8;color:#1c2330}
            header{padding:28px 32px;color:#fff}header.ok{background:linear-gradient(135deg,#1c9a5b,#34c47c)}header.bad{background:linear-gradient(135deg,#c4332f,#ef6a5b)}
            header h1{margin:0 0 6px;font-size:26px}header p{margin:0;opacity:.95}header code{background:rgba(255,255,255,.2);padding:1px 6px;border-radius:4px}
            main{max-width:1100px;margin:0 auto;padding:24px 20px 60px}h2{margin:34px 0 12px;font-size:20px}h3{margin:0 0 6px}h4{margin:14px 0 6px}
            .cards{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.c{background:#fff;border-radius:12px;padding:16px;text-align:center;box-shadow:0 1px 4px rgba(0,0,0,.06);font-size:13px;color:#667}
            .c span{display:block;font-size:30px;font-weight:700;margin-bottom:2px}.c.pass span{color:#1c9a5b}.c.fail span{color:#d23c36}.c.skip span{color:#b08a1a}.c.all span{color:#1c2330}
            .progress{height:10px;background:#e3e6ea;border-radius:6px;margin-top:16px;overflow:hidden}.progress div{height:100%;background:linear-gradient(90deg,#1c9a5b,#34c47c)}
            .muted{color:#778;font-size:13px}table{width:100%;border-collapse:collapse;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.06);font-size:14px}
            th,td{padding:8px 12px;text-align:left;border-bottom:1px solid #eef0f3;vertical-align:top}th{background:#fafbfc;font-weight:600;color:#556}
            td.p{color:#1c9a5b;font-weight:600}td.f{color:#d23c36;font-weight:600}td.s{color:#b08a1a;font-weight:600}
            tr.failed td{background:#fff5f4}tr.skipped td{color:#889}table.api{font-size:12px;margin:6px 0}table.api td{word-break:break-all}tr.pr td{background:#fff0ee}
            .card{background:#fff;border-radius:12px;padding:18px 20px;margin:12px 0;box-shadow:0 1px 4px rgba(0,0,0,.06);border-left:5px solid #ccc}
            .card.fail{border-color:#d23c36}.card.warn{border-color:#e0a82e}.why{font-weight:600;margin:4px 0}
            pre{background:#f3f4f6;padding:10px;border-radius:8px;white-space:pre-wrap;word-break:break-word;font-size:12.5px}
            code{background:#f0f2f5;padding:1px 5px;border-radius:4px;font-size:12.5px;word-break:break-all}img{max-width:100%;border:1px solid #dde;border-radius:8px;margin-top:10px}
            @media(max-width:700px){.cards{grid-template-columns:repeat(2,1fr)}}
            """;
}
