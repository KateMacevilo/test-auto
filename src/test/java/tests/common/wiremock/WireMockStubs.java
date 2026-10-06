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
        Map<String, Object> mapping = new LinkedHashMap<>();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", downstream.getMethod());
        request.put("urlPath", downstream.getUrlPath());
        // сопоставление по query-параметрам и header'ам — только точное совпадение (equalTo)
        if (downstream.getQueryParams() != null) {
            request.put("queryParameters", equalToMatchers(downstream.getQueryParams()));
        }
        if (downstream.getHeaders() != null) {
            request.put("headers", equalToMatchers(downstream.getHeaders()));
        }
        mapping.put("request", request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", downstream.getStatus());
        response.put("headers", Map.of("Content-Type", "application/json"));
        // строка — сырое тело, JSON-объект — через jsonBody (WireMock сериализует сам)
        if (downstream.getBody() instanceof String) {
            response.put("body", downstream.getBody());
        } else {
            response.put("jsonBody", downstream.getBody());
        }
        mapping.put("response", response);
        uploadStub(mapping);
    }

    /**
     * Предпроверка перед кейсом: стаб даунстрима отвечает ожидаемым статусом.
     * Тест сам бьёт в WireMock по заглушенному пути с query-параметрами и header'ами
     * даунстрима — если маппинг не выставился или отвечает иначе, кейс падает
     * до обращения к сервису.
     */
    public void verifyStubResponds(Downstream downstream) {
        validate(downstream);
        var request = given();
        if (downstream.getQueryParams() != null) {
            request = request.queryParams(downstream.getQueryParams());
        }
        if (downstream.getHeaders() != null) {
            request = request.headers(downstream.getHeaders());
        }
        request.request(downstream.getMethod(), wireMockUrl + downstream.getUrlPath())
                .then().statusCode(downstream.getStatus());
        log.info("WireMock stub responds: {} {} -> {}",
                downstream.getMethod(), downstream.getUrlPath(), downstream.getStatus());
    }

    /** Число обращений к даунстриму в журнале WireMock (до/после кейса — по приращению). */
    public long requestCount(Downstream downstream) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("method", downstream.getMethod());
        criteria.put("urlPath", downstream.getUrlPath());
        if (downstream.getQueryParams() != null) {
            criteria.put("queryParameters", equalToMatchers(downstream.getQueryParams()));
        }
        if (downstream.getHeaders() != null) {
            criteria.put("headers", equalToMatchers(downstream.getHeaders()));
        }
        return given().contentType(ContentType.JSON)
                .body(criteria)
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

    /** Матчеры WireMock вида {ключ: {equalTo: значение}} для query-параметров/header'ов. */
    private Map<String, Object> equalToMatchers(Map<String, String> criteria) {
        Map<String, Object> matchers = new LinkedHashMap<>();
        criteria.forEach((key, value) -> matchers.put(key, Map.of("equalTo", value)));
        return matchers;
    }
}
