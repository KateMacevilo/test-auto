package tests.common.model;

import lombok.Data;

import java.util.Map;

@Data
public class Input {
    private String contentType;
    private Map<String, String> headers;
    /** Тело запроса: Map/JsonNode — то, что RestAssured сериализует в JSON. */
    private Object body;
    /**
     * Path-шаблон GET-запроса (только GET-кейсы): "{consentId}" — согласие, созданное
     * кейсом на шаге 1, "{randomUuid}" — случайный UUID (кейсы "запись не найдена").
     * Не задан — GET по согласию, созданному кейсом.
     */
    private String path;
}
