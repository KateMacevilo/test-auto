package tests;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import tests.common.AbstractApiTest;
import tests.common.assertions.Assertions;
import tests.common.dataprovider.DataProviders;
import tests.common.model.DbState;
import tests.common.model.DbTable;
import tests.common.model.Input;
import tests.common.model.TestData;
import tests.common.wiremock.Downstream;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.qameta.allure.Allure.step;

/**
 * Единая точка входа прогона тест-кейсов сервиса api-listpassportsconsent.
 * Каждый тест-кейс — json-файл (или массив параметризаций в одном файле), связь с методом —
 * по имени: FileDP вычитывает файл с именем метода, AllFilesDP — все файлы из testdata/api/.
 * Простые кейсы «один запрос → один ответ» сводятся к универсальному createConsent;
 * сценарии с состоянием (идемпотентность) — отдельными методами ниже.
 */
@Slf4j
@Feature("Создание согласия на инициирование платежа")
public class Tests extends AbstractApiTest {

    private static final String CONFLUENCE = "https://confluence.example/display/API/api-listpassportsconsent";

    private static final String EXPECTED_ERROR_CODE = "BY.Rules.IllegalAttemptOfCreation";
    private static final int EXPECTED_CONFLICT_STATUS = 409;
    private static final BigDecimal AMOUNT_MISMATCH = new BigDecimal("999.99");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Link(name = "api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Health check")
    @Test(dataProvider = "FileDP", dataProviderClass = DataProviders.class, priority = 1)
    public void getHealth(TestData testData) {
        Response response = templateRequest.getHealth(baseRequest, url).extract().response();
        Assertions.verifyStatusCode(response, testData.getExpected().getStatusCode());
        Assertions.verifyResponseSchema(response, HEALTH_SCHEMA);
        Assertions.verifyResponseParam(response, testData.getExpected().getParams());
    }

    @Link(name = "api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Создание согласия (listPassports)")
    @Test(dataProvider = "AllFilesDP", dataProviderClass = DataProviders.class, priority = 2)
    public void createConsent(TestData testData) {
        sendAndVerifyConsent(testData);
    }

    /**
     * WireMock-кейсы: те же проверки, что у createConsent, но даунстримы сервиса заглушены.
     * Даунстримы и их ответы (метод, путь, статус, тело) полностью описаны в данных кейса —
     * списком downstreams, его порядок задаёт порядок предпроверки/проверки. Реестра
     * даунстримов в коде нет: каждый кейс объявляет только свои заглушки.
     * Перед кейсом стабы его даунстримов пересоздаются (свои прошлые удаляются по секциям,
     * выставляются ответы из данных), после завершения ВСЕХ тестов созданные стабы
     * удаляются по секциям (см. deleteWireMockStubs) — чужие маппинги на инстансе
     * не трогаются.
     * Прогон только локально: при wiremock.cases.enabled=false кейс скипается.
     * Поток кейса: стабы → предпроверка (в порядке списка) → счётчики обращений →
     * запрос к сервису → проверка, что сервис реально дёрнул каждый даунстрим сценария.
     * Нет downstreams в данных — без заглушек, предпроверка и проверка обращений пропускаются.
     */
    @Link(name = "api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Создание согласия (listPassports): WireMock")
    @Test(dataProvider = "WireMockDP", dataProviderClass = DataProviders.class, priority = 4)
    public void createConsentWithWireMock(TestData testData) {
        if (testData.isLocal() && !wireMockCasesEnabled) {
            throw new SkipException("Локальный WireMock-кейс пропущен: wiremock.cases.enabled=false (прогон в k8s)");
        }
        List<Downstream> downstreams = testData.getDownstreams() != null
                ? testData.getDownstreams() : List.of();
        // Шаг 1: стабы под этот кейс — свои прошлые удаляем, существующие (чужие/наши) не дублируем
        wireMockStubs.deleteCreatedStubs();
        downstreams.forEach(wireMockStubs::createStubIfAbsent);
        // Шаг 2: стабы отвечают как ожидает кейс (иначе смысла гонять его нет) — в порядке списка
        downstreams.forEach(wireMockStubs::verifyStubResponds);
        // Шаг 3: сколько раз даунстримы уже дёргались (журнал общий на прогон)
        Map<String, Long> countsBefore = downstreamRequestCounts(downstreams);
        // Шаг 4: сам кейс
        sendAndVerifyConsent(testData);
        // Шаг 5: сервис обратился к каждому даунстриму сценария (по приращению счётчика)
        Map<String, Long> countsAfter = downstreamRequestCounts(downstreams);
        countsAfter.forEach((downstream, count) -> Assert.assertTrue(count > countsBefore.get(downstream),
                "Сервис не обратился к даунстриму " + downstream));
    }

    private Map<String, Long> downstreamRequestCounts(List<Downstream> downstreams) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Downstream downstream : downstreams) {
            counts.put(downstream.getName(), wireMockStubs.requestCount(downstream));
        }
        return counts;
    }

    /** Удаляет созданные тестами стабы WireMock после завершения тестов (выполняется всегда). */
    @AfterClass(alwaysRun = true)
    public void deleteWireMockStubs() {
        if (!wireMockCasesEnabled) {
            return;
        }
        wireMockStubs.deleteCreatedStubs();
    }

    /** Общее тело прогона кейса создания согласия: POST → статус → (схема) → параметры → БД. */
    private void sendAndVerifyConsent(TestData testData) {
        Response response = templateRequest
                .createPaymentConsent(testData.getInput(), baseRequest, url)
                .extract().response();
        Assertions.verifyStatusCode(response, testData.getExpected().getStatusCode());
        if (Boolean.TRUE.equals(testData.getExpected().getVerifySchema())) {
            verifyConsentSchema(response);
        }
        Assertions.verifyResponseParam(response, testData.getExpected().getParams());
        verifyDbState(testData);
    }

    @Link(name = "api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Создание согласия (listPassports): идемпотентность")
    @Test(dataProvider = "FileDP", dataProviderClass = DataProviders.class, priority = 3)
    public void create_consent_idempotent_body_mismatch_conflict_409(TestData testData) {
        String idempotencyKey = uniqueIdempotencyKey();
        UUID consentUuid = null;

        try {
            // Шаг 1: первый запрос — полные валидные заголовки и тело, согласие создаётся в БД (amount=150.00)
            step("Шаг 1: POST /api/paymentConsents/listPassports — первый запрос, создание согласия",
                    () -> postConsentAndVerifyCreated(testData, idempotencyKey));

            // Шаг 2: проверка БД — x-idempotency-key найден, initiation сохранён и полностью совпадает с телом запроса
            consentUuid = step("Шаг 2: проверка БД — idempotency-key найден, initiation совпадает с запросом целиком",
                    () -> findConsentAndVerifyInitiation(idempotencyKey, testData));

            // Шаг 3: повторный запрос с тем же x-idempotency-key, но ТЕЛО НЕ СОВПАДАЕТ (amount=999.99) → 409 Conflict
            step("Шаг 3: повторный запрос с тем же ключом и другим телом — ожидается 409 Conflict", () -> {
                Input mismatchInput = withAmount(
                        withIdempotencyKey(testData.getInput(), idempotencyKey), AMOUNT_MISMATCH);
                Response conflict = templateRequest
                        .createPaymentConsent(mismatchInput, baseRequest, url)
                        .extract().response();
                Assertions.verifyStatusCode(conflict, EXPECTED_CONFLICT_STATUS);
                Map<String, Object> errorParams = new HashMap<>();
                errorParams.put("code", EXPECTED_ERROR_CODE);
                Assertions.verifyResponseParam(conflict, errorParams);
            });
        } finally {
            cleanupConsent(consentUuid);
        }
    }

    @Link(name = "api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Создание согласия (listPassports): идемпотентность")
    @Test(dataProvider = "FileDP", dataProviderClass = DataProviders.class, priority = 3)
    public void create_consent_idempotent_same_body_returns_201(TestData testData) {
        String idempotencyKey = uniqueIdempotencyKey();
        UUID consentUuid = null;

        try {
            // Шаг 1: первый запрос создаёт согласие; повторный запрос с тем же ключом и СОВПАДАЮЩИМ телом
            Response first = step("Шаг 1: POST — первый запрос, создание согласия",
                    () -> postConsentAndVerifyCreated(testData, idempotencyKey));

            // Шаг 2: проверка БД — ключ найден, initiation совпадает с сохранённым целиком
            consentUuid = step("Шаг 2: проверка БД — idempotency-key найден, initiation совпадает с запросом целиком",
                    () -> findConsentAndVerifyInitiation(idempotencyKey, testData));

            // Шаги 3-4: согласие существует, повторно не создаётся; ответ 201 Created (существующее согласие из БД)
            step("Шаги 3-4: повторный запрос с тем же ключом и совпадающим телом — 201, дубль не создаётся", () -> {
                Response second = postConsent(testData.getInput(), idempotencyKey);
                Assertions.verifyStatusCode(second, testData.getExpected().getStatusCode());
                verifyConsentSchema(second);
                Assertions.verifyResponseParam(second, testData.getExpected().getParams());
                Assert.assertEquals(
                        second.jsonPath().getString("data.listPassportsConsentId"),
                        first.jsonPath().getString("data.listPassportsConsentId"),
                        "Idempotent retry must return the existing consent, not create a new one");
            });
        } finally {
            cleanupConsent(consentUuid);
        }
    }

    /**
     * Проверка БД по x-idempotency-key кейса. Тип проверки — из данных кейса
     * (expected.dbState); если не задан, выводится из statusCode: 2xx → запись
     * удаляется после проверки (CLEANUP), 4xx/5xx → записей быть не должно (ABSENT).
     * Ожидаемые сохранённые строки — в expected.dbParams: на таблицу — список ожидаемых
     * строк ({колонка: значение}), напр. {"table": "multi_authorisation", "rows":
     * [{"sign_group_code": "1", "sign": "0"}, {"sign_group_code": "2", "sign": "0"}]}.
     */
    private void verifyDbState(TestData testData) {
        String idempotencyKey = testData.getInput().getHeaders() != null
                ? testData.getInput().getHeaders().get("x-idempotency-key")
                : null;
        if (idempotencyKey == null) {
            return;
        }
        DbState dbState = testData.getExpected().getDbState();
        if (dbState == null) {
            dbState = testData.getExpected().getStatusCode() < 300 ? DbState.CLEANUP : DbState.ABSENT;
        }
        switch (dbState) {
            case SKIP -> {
                // БД не проверяем
            }
            case ABSENT -> Assert.assertTrue(
                    dbClient.findConsentUuidByIdempotencyKey(idempotencyKey).isEmpty(),
                    "No consent records expected in DB for idempotency key " + idempotencyKey);
            case EXISTS, CLEANUP -> {
                UUID uuid = dbClient.findConsentUuidByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new AssertionError(
                                "Consent record expected in DB for idempotency key " + idempotencyKey));
                verifyDbParams(uuid, testData.getExpected().getDbParams());
                if (dbState == DbState.CLEANUP) {
                    dbClient.deleteAllDataConsentById(uuid);
                }
            }
        }
    }

    /**
     * Сверка сохранённых в БД строк с ожидаемыми из кейса (expected.dbParams).
     * На таблицу — один запрос, дальше каждая ожидаемая строка ищется среди фактических:
     * должна существовать строка, у которой все перечисленные в кейсе колонки равны
     * ожидаемым значениям (экзистенциальная проверка — так описываются и одиночные строки,
     * и наборы строк с разными значениями, напр. sign_group_code = 1 и 2).
     */
    private void verifyDbParams(UUID consentUuid, List<DbTable> dbParams) {
        if (dbParams == null) {
            return;
        }
        dbParams.forEach(dbTable -> {
            List<Map<String, String>> actualRows = dbClient.findRows(dbTable.getTable(), consentUuid);
            dbTable.getRows().forEach(expectedRow -> Assert.assertTrue(
                    actualRows.stream().anyMatch(actualRow -> matchesRow(actualRow, expectedRow)),
                    "No row in " + dbTable.getTable() + " matching " + expectedRow
                            + " for consent " + consentUuid + " — actual rows: " + actualRows));
        });
    }

    /** Строка таблицы совпадает с ожидаемой, если все перечисленные в ней колонки равны. */
    private boolean matchesRow(Map<String, String> actualRow, Map<String, String> expectedRow) {
        return expectedRow.entrySet().stream()
                .allMatch(e -> Objects.equals(actualRow.get(e.getKey()), e.getValue()));
    }

    // --- Общие шаги сценариев с состоянием ---

    private Response postConsent(Input input, String idempotencyKey) {
        return templateRequest
                .createPaymentConsent(withIdempotencyKey(input, idempotencyKey), baseRequest, url)
                .extract().response();
    }

    private Response postConsentAndVerifyCreated(TestData testData, String idempotencyKey) {
        Response response = postConsent(testData.getInput(), idempotencyKey);
        Assertions.verifyStatusCode(response, testData.getExpected().getStatusCode());
        verifyConsentSchema(response);
        Assertions.verifyResponseParam(response, testData.getExpected().getParams());
        log.info("Consent created, response: {}", response.getBody().asPrettyString());
        return response;
    }

    /**
     * Проверка БД шага 2 идемпотентных сценариев: согласие найдено по ключу, а сохранённый
     * в колонке initiation JSON сверяется с initiation тела запроса ЦЕЛИКОМ — по той же логике,
     * что isMatchWithExisting в сервисе (десериализация initiation и сравнение объектов).
     * Сверка: порядок полей в объектах не важен, числа сравниваются по значению (150 = 150.0),
     * порядок элементов в массивах важен. Сообщение об ошибке показывает путь к расхождению.
     */
    private UUID findConsentAndVerifyInitiation(String idempotencyKey, TestData testData) {
        UUID uuid = dbClient.findConsentUuidByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new AssertionError(
                        "Idempotency key " + idempotencyKey + " not found in DB"));
        String initiationJson = dbClient.findInitiationByConsentId(uuid)
                .orElseThrow(() -> new AssertionError(
                        "Initiation not found in DB for consent " + uuid));
        JsonNode actual = parseJson(initiationJson);
        JsonNode expected = expectedInitiation(testData);
        assertJsonEquals(actual, expected, "$");
        return uuid;
    }

    /** Рекурсивная сверка JSON: объекты — без учёта порядка полей, числа — по значению. */
    private void assertJsonEquals(JsonNode actual, JsonNode expected, String path) {
        if (actual == null || expected == null) {
            Assert.fail("Initiation mismatch at " + path + ": one side is missing (actual=" + actual
                    + ", expected=" + expected + ")");
        }
        if (actual.isNumber() && expected.isNumber()) {
            if (actual.decimalValue().compareTo(expected.decimalValue()) != 0) {
                Assert.fail("Initiation mismatch at " + path + ": expected " + expected
                        + ", but was " + actual);
            }
            return;
        }
        if (actual.getNodeType() != expected.getNodeType()) {
            Assert.fail("Initiation mismatch at " + path + ": expected " + expected.getNodeType()
                    + " (" + expected + "), but was " + actual.getNodeType() + " (" + actual + ")");
        }
        if (actual.isObject()) {
            List<String> actualFields = new ArrayList<>();
            actual.fieldNames().forEachRemaining(actualFields::add);
            List<String> expectedFields = new ArrayList<>();
            expected.fieldNames().forEachRemaining(expectedFields::add);
            if (!actualFields.equals(expectedFields)) {
                List<String> missing = new ArrayList<>(expectedFields);
                missing.removeAll(actualFields);
                List<String> extra = new ArrayList<>(actualFields);
                extra.removeAll(expectedFields);
                Assert.fail("Initiation mismatch at " + path + ": missing fields " + missing
                        + ", extra fields " + extra);
            }
            actualFields.forEach(field -> assertJsonEquals(actual.get(field), expected.get(field),
                    path + "." + field));
        } else if (actual.isArray()) {
            if (actual.size() != expected.size()) {
                Assert.fail("Initiation mismatch at " + path + ": expected array of size "
                        + expected.size() + ", but was " + actual.size());
            }
            for (int i = 0; i < actual.size(); i++) {
                assertJsonEquals(actual.get(i), expected.get(i), path + "[" + i + "]");
            }
        } else if (!actual.equals(expected)) {
            Assert.fail("Initiation mismatch at " + path + ": expected " + expected
                    + ", but was " + actual);
        }
    }

    /** Initiation из тела запроса кейса (/listPassportsConsentRequest/data/initiation). */
    private JsonNode expectedInitiation(TestData testData) {
        return MAPPER.valueToTree(testData.getInput().getBody())
                .at("/listPassportsConsentRequest/data/initiation");
    }

    private JsonNode parseJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Failed to parse initiation JSON from DB: " + json, e);
        }
    }

    private void cleanupConsent(UUID consentUuid) {
        UUID toDelete = consentUuid;
        step("Очистка БД: удаление записей согласия " + toDelete, () -> {
            if (toDelete != null) {
                dbClient.deleteAllDataConsentById(toDelete);
            }
        });
    }

    private String uniqueIdempotencyKey() {
        // Уникальный ключ на каждый прогон, чтобы тесты не мешали друг другу и легко чистились из БД
        return "idem-key-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    /** Возвращает копию input с подставленным уникальным x-idempotency-key. */
    private Input withIdempotencyKey(Input input, String idempotencyKey) {
        Input copy = copy(input);
        Map<String, String> headers = new HashMap<>(input.getHeaders());
        headers.put("x-idempotency-key", idempotencyKey);
        copy.setHeaders(headers);
        return copy;
    }

    /** Возвращает копию input с изменённым amount в теле запроса. */
    private Input withAmount(Input input, BigDecimal amount) {
        Input copy = copy(input);
        JsonNode initiation = ((JsonNode) copy.getBody()).at("/listPassportsConsentRequest/data/initiation");
        ((ObjectNode) initiation).put("amount", amount);
        return copy;
    }

    private Input copy(Input input) {
        Input copy = new Input();
        copy.setContentType(input.getContentType());
        copy.setHeaders(input.getHeaders());
        copy.setBody(MAPPER.valueToTree(input.getBody()));
        return copy;
    }
}
