package uz.aros.autotests.reporting;

import java.util.List;

/** Итог одного теста — то, что попадает в консоль, Telegram и HTML-отчёт. */
public final class TestRecord {

    public enum Status {PASSED, FAILED, SKIPPED}

    public final String uniqueId;
    public final String caseId;          // AUTH-001 или null
    public final String module;
    public final String title;
    public final String className;
    public final String methodName;
    public final List<String> tags;
    public final Status status;
    public final long durationMs;
    public final FailureAnalyzer.Reason reason;   // для FAILED/SKIPPED
    public final TestDiagnostics diagnostics;     // может быть null (тесты без браузера)

    public TestRecord(String uniqueId, String caseId, String title, String className, String methodName,
                      List<String> tags, Status status, long durationMs,
                      FailureAnalyzer.Reason reason, TestDiagnostics diagnostics) {
        this.uniqueId = uniqueId;
        this.caseId = caseId;
        this.module = ModuleNames.moduleOf(caseId);
        this.title = title;
        this.className = className;
        this.methodName = methodName;
        this.tags = tags;
        this.status = status;
        this.durationMs = durationMs;
        this.reason = reason;
        this.diagnostics = diagnostics;
    }

    /** «AUTH-001 · Название» или просто название, если ID нет. */
    public String label() {
        return caseId == null ? title : caseId + " · " + title;
    }

    public boolean hasBackendProblems() {
        return diagnostics != null && diagnostics.problems().stream().anyMatch(ApiCall::isServerError);
    }
}
