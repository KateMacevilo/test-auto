package tests.common.wiremock;

import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Переиспользуемые заглушки WireMock для даунстримов сервиса (реестр — enum {@link Downstream}).
 * Каждый публичный метод работает с даунстримом из реестра; стабы создаются через Admin API
 * WireMock, это обычный HTTP/JSON API — внутри простые вызовы RestAssured, отдельный
 * HTTP-клиент (RestTemplate/Feign) или зависимость wiremock-сервера не нужны.
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

    /** Создаёт стаб для даунстрима (через Admin API). */
    public void createStub(Downstream downstream) {
        createStub(downstream, null);
    }

    /**
     * Создаёт стаб для даунстрима с переопределением ответа из кейса (null — дефолт даунстрима).
     * Не заданные в override поля берутся из enum Downstream.
     */
    public void createStub(Downstream downstream, StubResponse override) {
        int status = override != null && override.getStatus() != 0
                ? override.getStatus() : downstream.getStatus();
        Object body = override != null && override.getBody() != null
                ? override.getBody() : downstream.getBody();
        uploadStub(downstream.getMethod(), downstream.getUrlPath(), status, body);
    }

    /** Создаёт стабы всех известных даунстримов (полный набор перед прогоном). */
    public void createAllStubs() {
        for (Downstream downstream : Downstream.values()) {
            createStub(downstream);
        }
    }

    /**
     * Предпроверка перед кейсом: стаб даунстрима отвечает ожидаемым статусом
     * (с учётом переопределения из кейса, null — дефолт даунстрима).
     * Тест сам бьёт в WireMock по заглушенному пути — если маппинг не выставился
     * или отвечает иначе, кейс падает до обращения к сервису.
     */
    public void verifyStubResponds(Downstream downstream, StubResponse override) {
        int status = override != null && override.getStatus() != 0
                ? override.getStatus() : downstream.getStatus();
        verifyStubResponds(downstream.getMethod(), downstream.getUrlPath(), status);
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

    private void verifyStubResponds(String method, String urlPath, int expectedStatus) {
        given().request(method, wireMockUrl + urlPath)
                .then().statusCode(expectedStatus);
        log.info("WireMock stub responds: {} {} -> {}", method, urlPath, expectedStatus);
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
