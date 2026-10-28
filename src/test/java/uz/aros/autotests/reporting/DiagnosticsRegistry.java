package uz.aros.autotests.reporting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище диагностики: «текущий тест этого потока» + «готовые данные по uniqueId теста».
 *
 * <p>Тест и слушатель результатов живут в разных местах JUnit, поэтому связываем их по
 * {@code uniqueId} теста — он один и тот же и в {@code ExtensionContext}, и в {@code TestIdentifier}.
 */
public final class DiagnosticsRegistry {

    private static final ThreadLocal<TestDiagnostics> CURRENT = new ThreadLocal<>();
    private static final Map<String, TestDiagnostics> BY_TEST = new ConcurrentHashMap<>();

    private DiagnosticsRegistry() {
    }

    public static TestDiagnostics begin(String testUniqueId) {
        TestDiagnostics d = new TestDiagnostics(testUniqueId);
        CURRENT.set(d);
        BY_TEST.put(testUniqueId, d);
        return d;
    }

    /** Диагностика текущего теста или null, если вызывают вне теста. */
    public static TestDiagnostics current() {
        return CURRENT.get();
    }

    public static void endCurrent() {
        CURRENT.remove();
    }

    public static TestDiagnostics forTest(String testUniqueId) {
        return BY_TEST.get(testUniqueId);
    }
}
