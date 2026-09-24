package tests.common.model;

import lombok.Data;

import java.util.Map;

@Data
public class Input {
    private String contentType;
    private Map<String, String> headers;
    /** Тело запроса: Map/JsonNode — то, что RestAssured сериализует в JSON. */
    private Object body;
}
