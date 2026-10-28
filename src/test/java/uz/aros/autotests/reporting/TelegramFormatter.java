package uz.aros.autotests.reporting;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Собирает красивый отчёт для Telegram в HTML-разметке (поддерживаются теги b, i, code, pre).
 *
 * <p>Сообщение в Telegram не может быть длиннее 4096 символов, поэтому результат — список сообщений:
 * сводка, потом упавшие тесты порциями, потом предупреждения.
 */
public final class TelegramFormatter {

    private static final int LIMIT = 3800;     // с запасом от 4096
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private TelegramFormatter() {
    }

    public static List<String> format(RunReport r) {
        List<String> blocks = new ArrayList<>();
        blocks.add(summary(r));

        if (!r.failed().isEmpty()) {
            blocks.add("<b>❌ Упавшие тесты</b>");
            int i = 1;
            for (TestRecord t : r.failed()) blocks.add(failure(i++, t));
        }
        if (!r.passedWithWarnings().isEmpty()) {
            blocks.add(warnings(r));
        }
        if (!r.skipped().isEmpty()) {
            blocks.add(skipped(r));
        }
        if (r.allGreen()) {
            blocks.add("🎉 <b>Всё зелёное.</b> Критичных проблем не найдено.");
        }
        return pack(blocks);
    }

    // ---------------------------------------------------------------- блоки

    private static String summary(RunReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.allGreen() ? "✅ <b>Aros UI — прогон успешен</b>" : "🔴 <b>Aros UI — есть падения</b>").append("\n");
        sb.append("🌐 ").append(esc(r.baseUrl)).append("\n");
        sb.append("🕒 ").append(TIME.format(r.startedAt)).append("  ·  ⏱ ").append(duration(r.durationMs)).append("\n");
        if (!r.label.isBlank()) sb.append("▶️ Запуск: <code>").append(esc(r.label)).append("</code>");
        if (!r.triggeredBy.isBlank()) sb.append("  ·  👤 ").append(esc(r.triggeredBy));
        if (!r.label.isBlank() || !r.triggeredBy.isBlank()) sb.append("\n");
        if (!r.commit.isBlank()) sb.append("🔖 commit <code>").append(esc(r.commit)).append("</code>\n");
        sb.append("\n");
        sb.append("<b>").append(r.passRatePercent()).append("%</b>  ").append(bar(r.passRatePercent())).append("\n");
        sb.append("✅ Прошло: <b>").append(r.count(TestRecord.Status.PASSED)).append("</b>   ")
                .append("❌ Упало: <b>").append(r.count(TestRecord.Status.FAILED)).append("</b>   ")
                .append("⏭ Пропущено: <b>").append(r.count(TestRecord.Status.SKIPPED)).append("</b>   ")
                .append("Всего: ").append(r.total()).append("\n\n");

        sb.append("<b>По модулям</b>\n");
        for (Map.Entry<String, int[]> e : r.byModule().entrySet()) {
            int[] v = e.getValue();
            int total = v[0] + v[1] + v[2];
            String icon = v[1] > 0 ? "❌" : (v[2] == total ? "⏭" : "✅");
            sb.append(icon).append(" ").append(esc(e.getKey())).append(" — ").append(v[0]).append("/").append(total);
            if (v[1] > 0) sb.append("  (упало ").append(v[1]).append(")");
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String failure(int n, TestRecord t) {
        StringBuilder sb = new StringBuilder();
        sb.append(n).append(") ");
        if (t.caseId != null) sb.append("<code>").append(esc(t.caseId)).append("</code> ");
        sb.append("<b>").append(esc(t.title)).append("</b>\n");
        FailureAnalyzer.Reason why = t.reason;
        if (why != null) {
            sb.append("📍 <b>Причина:</b> ").append(esc(why.headline())).append("\n");
            if (!why.detail().isBlank()) sb.append("🧾 ").append(esc(why.detail())).append("\n");
            if (!why.element().isBlank()) sb.append("🔎 Элемент: <code>").append(esc(why.element())).append("</code>\n");
        }
        TestDiagnostics d = t.diagnostics;
        if (d != null && !d.lastUrl().isBlank()) sb.append("🔗 Страница: ").append(esc(d.lastUrl())).append("\n");
        if (d != null) sb.append(endpoints(d));
        if (why != null && !why.location().isBlank()) {
            sb.append("📄 ").append(esc(t.className.substring(t.className.lastIndexOf('.') + 1))).append(" (").append(esc(why.location())).append(")\n");
        }
        return sb.toString().stripTrailing();
    }

    /** Эндпоинты с ошибками (если были) или последние запросы — чтобы было видно, что делал фронт перед падением. */
    private static String endpoints(TestDiagnostics d) {
        StringBuilder sb = new StringBuilder();
        List<ApiCall> problems = d.problems();
        if (!problems.isEmpty()) {
            sb.append("🛰 <b>Ошибки эндпоинтов:</b>\n");
            int shown = 0;
            for (ApiCall c : problems) {
                if (shown++ == 4) {
                    sb.append("   … и ещё ").append(problems.size() - 4).append("\n");
                    break;
                }
                sb.append("   • <code>").append(esc(c.method())).append(" ").append(esc(path(c.url()))).append("</code> → <b>")
                        .append(c.status() == 0 ? "обрыв" : c.status()).append("</b>");
                if (c.statusText() != null && !c.statusText().isBlank()) sb.append(" ").append(esc(c.statusText()));
                if (c.durationMs() >= 0) sb.append(" · ").append(c.durationMs()).append(" мс");
                sb.append("\n");
                if (!c.requestBody().isBlank()) sb.append("      ➡ <i>").append(esc(cut(c.requestBody(), 160))).append("</i>\n");
                if (!c.responseBody().isBlank()) sb.append("      ⬅ <i>").append(esc(cut(c.responseBody(), 220))).append("</i>\n");
            }
        } else {
            List<ApiCall> recent = d.recentCalls();
            if (!recent.isEmpty()) {
                sb.append("🛰 Последние запросы (ошибок бэкенда нет):\n");
                int from = Math.max(0, recent.size() - 3);
                for (ApiCall c : recent.subList(from, recent.size())) {
                    sb.append("   • <code>").append(esc(c.method())).append(" ").append(esc(path(c.url()))).append("</code> → ")
                            .append(c.status()).append("\n");
                }
            }
        }
        if (!d.consoleErrors().isEmpty()) {
            sb.append("🖥 Ошибка в консоли браузера: <i>").append(esc(cut(d.consoleErrors().get(0), 200))).append("</i>\n");
        }
        return sb.toString();
    }

    private static String warnings(RunReport r) {
        StringBuilder sb = new StringBuilder("<b>⚠️ Прошли, но есть подозрительное</b>\n");
        int n = 0;
        for (TestRecord t : r.passedWithWarnings()) {
            if (n++ == 8) {
                sb.append("… и ещё ").append(r.passedWithWarnings().size() - 8).append("\n");
                break;
            }
            sb.append("• ");
            if (t.caseId != null) sb.append("<code>").append(esc(t.caseId)).append("</code> ");
            sb.append(esc(t.title)).append("\n");
            for (ApiCall c : t.diagnostics.problems()) {
                sb.append("    ").append(esc(c.method())).append(" ").append(esc(path(c.url()))).append(" → ")
                        .append(c.status() == 0 ? "обрыв" : c.status()).append("\n");
                break;
            }
            if (t.diagnostics.problems().isEmpty() && !t.diagnostics.consoleErrors().isEmpty()) {
                sb.append("    консоль: ").append(esc(cut(t.diagnostics.consoleErrors().get(0), 140))).append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String skipped(RunReport r) {
        StringBuilder sb = new StringBuilder("<b>⏭ Пропущено</b>\n");
        int n = 0;
        for (TestRecord t : r.skipped()) {
            if (n++ == 10) {
                sb.append("… и ещё ").append(r.skipped().size() - 10).append("\n");
                break;
            }
            sb.append("• ");
            if (t.caseId != null) sb.append("<code>").append(esc(t.caseId)).append("</code> ");
            sb.append(esc(t.title));
            if (t.reason != null && !t.reason.headline().isBlank()) sb.append(" — <i>").append(esc(cut(t.reason.headline(), 120))).append("</i>");
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }

    // ---------------------------------------------------------------- утилиты

    /** Склеивает блоки в сообщения не длиннее лимита Telegram. */
    static List<String> pack(List<String> blocks) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String b : blocks) {
            String piece = b.length() > LIMIT ? b.substring(0, LIMIT) + "…" : b;
            if (cur.length() + piece.length() + 2 > LIMIT) {
                out.add(cur.toString());
                cur = new StringBuilder();
            }
            if (cur.length() > 0) cur.append("\n\n");
            cur.append(piece);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    static String bar(int percent) {
        int filled = Math.round(percent / 10.0f);
        return "▓".repeat(filled) + "░".repeat(10 - filled);
    }

    static String duration(long ms) {
        long s = ms / 1000;
        return s >= 60 ? (s / 60) + "м " + (s % 60) + "с" : s + "с";
    }

    /** Из полного URL оставляем путь и запрос: https://api.aros.uz/v1/cart?x=1 → /v1/cart?x=1 (хост и так понятен). */
    static String path(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            String p = u.getRawPath() == null ? "" : u.getRawPath();
            if (u.getRawQuery() != null) p += "?" + u.getRawQuery();
            return u.getHost() != null ? u.getHost() + p : url;
        } catch (Exception e) {
            return url;
        }
    }

    static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** Экранирование под HTML-режим Telegram: только &lt; &gt; &amp; */
    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
