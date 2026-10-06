package tests.common.wiremock;

import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Переиспользуемые заглушки WireMock для даунстримов сервиса. Даунстрим ({@link Downstream})
 * полностью описан в json кейса — метод, путь, статус и тело ответа, — классу остаётся
 * только создать стаб через Admin API WireMock (обычный HTTP/JSON API, внутри простые
 * вызовы RestAssured; отдельный HTTP-клиент (RestTemplate/Feign) или зависимость
 * wiremock-сервера не нужны).
 * Методы предназначены для вызова из тестов напрямую — так вызовы переиспользуются между кейсами.
 *
 * AGENTS.md: WireMock не поднимается тестами — адрес Admin API задаётся wiremock.url,
 * доступность WireMock из тестируемого сервиса — инфраструктурное требование.
 */
@Slf4j
@Component
public class WireMockStubs {

    private final String wireMockUrl;

    public WireMockStubs(@Value("${wiremock.url}") String wireMockUrl) {
        this.wireMockUrl = wireMockUrl;
    }

    /** Создаёт стаб для даунстрима (через Admin API) с ответом из описания даунстрима. */
    public void createStub(Downstream downstream) {
        validate(downstream);
        uploadStub(downstream.getMethod(), downstream.getUrlPath(),
                downstream.getStatus(), downstream.getBody());
    }

    /**
     * Предпроверка перед кейсом: стаб даунстрима отвечает ожидаемым статусом.
     * Тест сам бьёт в WireMock по заглушенному пути — если маппинг не выставился
     * или отвечает иначе, кейс падает до обращения к сервису.
     */
    public void verifyStubResponds(Downstream downstream) {
        validate(downstream);
        given().request(downstream.getMethod(), wireMockUrl + downstream.getUrlPath())
                .then().statusCode(downstream.getStatus());
        log.info("WireMock stub responds: {} {} -> {}",
                downstream.getMethod(), downstream.getUrlPath(), downstream.getStatus());
    }

    /** Число обращений к даунстриму в журнале WireMock (до/после кейса — по приращению). */
    public long requestCount(Downstream downstream) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("method", downstream.getMethod(), "urlPath", downstream.getUrlPath()))
                .post(wireMockUrl + "/__admin/requests/count")
                .then().statusCode(200)
                .extract().jsonPath().getLong("count");
    }

    /** Удаляет все маппинги (очистка WireMock после завершения всех тестов). */
    public void deleteAllMappings() {
        given().post(wireMockUrl + "/__admin/mappings/reset")
                .then().statusCode(200);
        log.info("WireMock mappings deleted");
    }

    /** Загрузка произвольного маппинга в нативном формате WireMock. */
    public void uploadStub(Object mapping) {
        given().contentType(ContentType.JSON).body(mapping)
                .post(wireMockUrl + "/__admin/mappings")
                .then().statusCode(201);
        log.info("WireMock stub created: {}", mapping);
    }

    private void validate(Downstream downstream) {
        if (downstream.getMethod() == null || downstream.getUrlPath() == null) {
            throw new IllegalArgumentException(
                    "Downstream '" + downstream.getName() + "' must define method and urlPath");
        }
    }

    private void uploadStub(String method, String urlPath, int status, Object body) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("request", Map.of("method", method, "urlPath", urlPath));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status);
        response.put("headers", Map.of("Content-Type", "application/json"));
        // строка — сырое тело, JSON-объект — через jsonBody (WireMock сериализует сам)
        if (body instanceof String) {
            response.put("body", body);
        } else {
            response.put("jsonBody", body);
        }
        mapping.put("response", response);
        uploadStub(mapping);
    }
}
