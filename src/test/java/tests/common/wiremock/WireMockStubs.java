package tests.common.wiremock;

import io.restassured.http.ContentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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

    /**
     * Id стабов, созданных этим компонентом за прогон — удаляем только их (DELETE by id).
     * AGENTS.md: инициализация ленивая — в некоторых окружениях (проксирование бина
     * Spring'ом) поле может остаться null, поэтому доступ только через createdIds().
     * Удаление по секциям (remove-by-metadata) не используем — в части версий WireMock
     * endpoint работает нестабильно (422); id надежнее.
     */
    private Set<String> createdStubIds;

    public WireMockStubs(@Value("${wiremock.url}") String wireMockUrl) {
        this.wireMockUrl = wireMockUrl;
    }

    /**
     * Создаёт стаб для даунстрима, если стаб с таким методом и путем ещё не выставлен
     * (проверка по списку маппингов инстанса) — повторное создание не нужно.
     */
    public void createStubIfAbsent(Downstream downstream) {
        if (stubExists(downstream)) {
            log.info("WireMock stub already exists: {} {} — skipping creation",
                    downstream.getMethod(), downstream.getUrlPath());
            return;
        }
        createStub(downstream);
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
        // имя и секция (поле Section в UI WireMock) — для навигации в UI; удаление — по id
        if (downstream.getName() != null) {
            mapping.put("name", downstream.getName());
        }
        if (downstream.getSection() != null) {
            mapping.put("metadata", Map.of("section", downstream.getSection()));
        }
        createdIds().add(uploadStub(mapping));
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

    /**
     * Удаляет стабы, созданные этим компонентом за прогон, по их id
     * (DELETE /__admin/mappings/{id} — чужие маппинги на инстансе не трогает).
     * Безопасно вызывать повторно и когда ничего не создавалось.
     */
    public void deleteCreatedStubs() {
        Set<String> ids = createdIds();
        ids.forEach(id -> {
            var response = given().delete(wireMockUrl + "/__admin/mappings/" + id)
                    .then().extract().response();
            // 404 — стаб уже удалён (вручную/другим прогоном) — не ошибка; прогон не ломаем
            if (response.statusCode() == 404) {
                log.warn("WireMock stub {} already absent", id);
                return;
            }
            if (response.statusCode() != 200) {
                // тело ответа — детали ошибки WireMock, без него диагностика невозможна
                throw new IllegalStateException("Delete stub failed for id '" + id
                        + "': " + response.statusCode() + " " + response.body().asString());
            }
        });
        if (!ids.isEmpty()) {
            log.info("WireMock stubs deleted: {} mappings", ids.size());
        }
        ids.clear();
    }

    /** true, если на инстансе уже есть стаб с тем же методом и путем. */
    private boolean stubExists(Downstream downstream) {
        var response = given().get(wireMockUrl + "/__admin/mappings").then().extract().response();
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to list WireMock mappings: "
                    + response.statusCode() + " " + response.body().asString());
        }
        return response.jsonPath().<Map<String, Object>>getList("mappings").stream()
                .anyMatch(mapping -> {
                    Map<String, Object> request = (Map<String, Object>) mapping.get("request");
                    return request != null
                            && downstream.getMethod().equals(request.get("method"))
                            && downstream.getUrlPath().equals(request.get("urlPath"));
                });
    }

    /** Удаляет ВСЕ маппинги (полный сброс инстанса; чужие стабы тоже удалятся). */
    public void deleteAllMappings() {
        given().post(wireMockUrl + "/__admin/mappings/reset")
                .then().statusCode(200);
        log.info("WireMock mappings deleted");
    }

    /**
     * Загрузка произвольного маппинга в нативном формате WireMock.
     * Возвращает id созданного стаба (при создании через createStub id запоминается
     * для deleteCreatedStubs; при прямом вызове uploadStub стаб в учёт не берётся).
     */
    public String uploadStub(Object mapping) {
        var response = given().contentType(ContentType.JSON).body(mapping)
                .post(wireMockUrl + "/__admin/mappings")
                .then().statusCode(201)
                .extract().response();
        log.info("WireMock stub created: {}", mapping);
        return response.jsonPath().getString("id");
    }

    private void validate(Downstream downstream) {
        if (downstream.getMethod() == null || downstream.getUrlPath() == null) {
            throw new IllegalArgumentException(
                    "Downstream '" + downstream.getName() + "' must define method and urlPath");
        }
    }

    /** Ленивый доступ к createdStubIds — поле может быть null (см. объявление). */
    private Set<String> createdIds() {
        if (createdStubIds == null) {
            createdStubIds = new LinkedHashSet<>();
        }
        return createdStubIds;
    }

    /** Матчеры WireMock вида {ключ: {equalTo: значение}} для query-параметров/header'ов. */
    private Map<String, Object> equalToMatchers(Map<String, String> criteria) {
        Map<String, Object> matchers = new LinkedHashMap<>();
        criteria.forEach((key, value) -> matchers.put(key, Map.of("equalTo", value)));
        return matchers;
    }
}
