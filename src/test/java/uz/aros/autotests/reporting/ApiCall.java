package uz.aros.autotests.reporting;

/**
 * Один сетевой запрос, который страница сделала во время теста (fetch/XHR или навигация).
 *
 * <p>Нужен для отчётов: когда тест падает, по списку таких запросов видно, не вернул ли
 * бэкенд ошибку (например, {@code POST /api/orders → 500}), а не только «элемент не найден».
 *
 * @param method        HTTP-метод (GET, POST…)
 * @param url           полный адрес запроса
 * @param status        HTTP-статус; 0, если запрос не завершился (сеть, обрыв)
 * @param statusText    текст статуса («Internal Server Error») или причина обрыва
 * @param durationMs    сколько запрос длился (мс), -1 если неизвестно
 * @param requestBody   тело запроса (пароли замаскированы), может быть пустым
 * @param responseBody  начало тела ответа — сохраняется только для ошибок
 * @param resourceType  fetch / xhr / document / …
 */
public record ApiCall(String method, String url, int status, String statusText, long durationMs,
                      String requestBody, String responseBody, String resourceType) {

    /** Ошибка = сервер ответил 4xx/5xx либо запрос оборвался. */
    public boolean isProblem() {
        return status == 0 || status >= 400;
    }

    /** Серверная ошибка (5xx) — самая серьёзная. */
    public boolean isServerError() {
        return status >= 500;
    }

    /** Короткая запись для логов: {@code POST https://api/x → 500 (Internal Server Error) 812ms}. */
    public String oneLine() {
        String st = status == 0 ? "НЕ ЗАВЕРШЁН" : String.valueOf(status);
        String text = (statusText == null || statusText.isBlank()) ? "" : " (" + statusText + ")";
        String time = durationMs >= 0 ? " · " + durationMs + " мс" : "";
        return method + " " + url + " → " + st + text + time;
    }
}
