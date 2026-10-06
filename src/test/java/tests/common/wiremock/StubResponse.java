package tests.common.wiremock;

import lombok.Data;

/**
 * Переопределение ответа заглушки для даунстрима в конкретном тест-кейсе
 * (элемент поля stubResponses в json, ключ — имя enum {@link Downstream}).
 * Не заданные поля берутся из дефолта даунстрима.
 */
@Data
public class StubResponse {
    /** HTTP-статус ответа заглушки (по умолчанию — из enum Downstream) */
    private int status;
    /** Тело ответа: JSON-объект или строка (по умолчанию — из enum Downstream) */
    private Object body;
}
