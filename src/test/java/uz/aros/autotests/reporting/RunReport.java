package uz.aros.autotests.reporting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Весь прогон целиком: метаданные + список {@link TestRecord}. Из него строятся все отчёты. */
public final class RunReport {

    public final String baseUrl;
    public final String locale;
    public final String label;          // что запускали: /smoke, /id AUTH-001…
    public final String triggeredBy;
    public final String commit;
    public final Instant startedAt;
    public final long durationMs;
    public final List<TestRecord> records;

    public RunReport(String baseUrl, String locale, String label, String triggeredBy, String commit,
                     Instant startedAt, long durationMs, List<TestRecord> records) {
        this.baseUrl = baseUrl;
        this.locale = locale;
        this.label = label;
        this.triggeredBy = triggeredBy;
        this.commit = commit;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.records = records;
    }

    public long count(TestRecord.Status s) {
        return records.stream().filter(r -> r.status == s).count();
    }

    public int total() {
        return records.size();
    }

    public int passRatePercent() {
        long executed = count(TestRecord.Status.PASSED) + count(TestRecord.Status.FAILED);
        return executed == 0 ? 0 : (int) Math.round(100.0 * count(TestRecord.Status.PASSED) / executed);
    }

    public List<TestRecord> failed() {
        return records.stream().filter(r -> r.status == TestRecord.Status.FAILED).toList();
    }

    public List<TestRecord> skipped() {
        return records.stream().filter(r -> r.status == TestRecord.Status.SKIPPED).toList();
    }

    /** Тесты, которые прошли, но на фоне бэкенд вернул 5xx или в консоли были ошибки — «зелёное, но подозрительное». */
    public List<TestRecord> passedWithWarnings() {
        List<TestRecord> out = new ArrayList<>();
        for (TestRecord r : records) {
            if (r.status == TestRecord.Status.PASSED && r.diagnostics != null
                    && (r.hasBackendProblems() || !r.diagnostics.consoleErrors().isEmpty())) {
                out.add(r);
            }
        }
        return out;
    }

    /** Модуль → {прошло, всего}; сохраняет порядок появления. */
    public Map<String, int[]> byModule() {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (TestRecord r : records) {
            int[] v = m.computeIfAbsent(r.module, k -> new int[3]);   // [passed, failed, skipped]
            v[r.status.ordinal()]++;
        }
        return m;
    }

    public boolean allGreen() {
        return count(TestRecord.Status.FAILED) == 0;
    }
}
