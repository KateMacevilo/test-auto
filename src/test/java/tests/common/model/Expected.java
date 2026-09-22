package tests.common.model;

import lombok.Data;

import java.util.Map;

@Data
public class Expected {
    private int statusCode;
    private Map<String, Object> params;
    /** Проверять ответ по JSON-схеме согласия (для позитивных 2xx-кейсов) */
    private Boolean verifySchema;
}
