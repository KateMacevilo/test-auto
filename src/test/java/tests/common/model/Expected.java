package tests.common.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Expected {
    private int statusCode;
    private Map<String, Object> params;
    /** Проверять ответ по JSON-схеме согласия (для позитивных 2xx-кейсов) */
    private Boolean verifySchema;
    /**
     * Ожидаемое состояние БД после запроса. Не задано — выводится из statusCode:
     * 2xx → CLEANUP, 4xx/5xx → ABSENT.
     */
    private DbState dbState;
    /** Проверки сохранённых в БД строк: таблица + набор ожидаемых строк ({колонка: значение}) */
    private List<DbTable> dbParams;
}
