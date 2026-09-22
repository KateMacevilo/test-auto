package tests.prior_ob_svc_api_listpassportsconsent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;
import tests.common.AbstractApiTest;
import tests.common.assertions.Assertions;
import tests.common.dataprovider.DataProviders;
import tests.common.model.Input;
import tests.common.model.TestData;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.qameta.allure.Allure.step;

/**
 * Сценарии создания согласия с состоянием (несколько зависимых запросов, проверки БД
 * между ними). Простые кейсы «один запрос → один ответ» прогоняются универсальным
 * методом Tests.createConsent из файлов testdata/api/.
 */
@Slf4j
@Feature("Создание согласия на инициирование платежа")
public class CreatePaymentConsentTest extends AbstractApiTest {

    private static final String CONFLUENCE = "https://confluence.priorbank.by:8443/display/API/prior-ob-svc-api-listpassportsconsent";

    private static final String EXPECTED_ERROR_CODE = "BY.PRIORBANK.Rules.IllegalAttemptOfCreation";
    private static final int EXPECTED_CONFLICT_STATUS = 409;
    private static final BigDecimal AMOUNT_IN_DB = new BigDecimal("150.00");
    private static final BigDecimal AMOUNT_MISMATCH = new BigDecimal("999.99");

    @Link(name = "prior-ob-svc-api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Создание согласия (listPassports): идемпотентность")
    @Test(dataProvider = "MainDP", dataProviderClass = DataProviders.class)
    public void create_consent_idempotent_body_mismatch_conflict_409(TestData testData) {
        String idempotencyKey = uniqueIdempotencyKey();
        UUID consentUuid = null;

        try {
            // Шаг 1: первый запрос — полные валидные заголовки и тело, согласие создаётся в БД (amount=150.00)
            Response first = step("Шаг 1: POST /api/paymentConsents/listPassports — первый запрос, создание согласия",
                    () -> postConsentAndVerifyCreated(testData, idempotencyKey));

            // Шаг 2: проверка БД — x-idempotency-key найден, тело сохранено (amount=150.00)
            consentUuid = step("Шаг 2: проверка БД — idempotency-key найден, amount=150.00",
                    () -> findConsentAndVerifyAmount(idempotencyKey));

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

    @Link(name = "prior-ob-svc-api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Создание согласия (listPassports): идемпотентность")
    @Test(dataProvider = "MainDP", dataProviderClass = DataProviders.class)
    public void create_consent_idempotent_same_body_returns_201(TestData testData) {
        String idempotencyKey = uniqueIdempotencyKey();
        UUID consentUuid = null;

        try {
            // Шаг 1: первый запрос создаёт согласие; повторный запрос с тем же ключом и СОВПАДАЮЩИМ телом
            Response first = step("Шаг 1: POST — первый запрос, создание согласия",
                    () -> postConsentAndVerifyCreated(testData, idempotencyKey));

            // Шаг 2: проверка БД — ключ найден, тело совпадает с сохранённым
            consentUuid = step("Шаг 2: проверка БД — idempotency-key найден, тело совпадает с сохранённым",
                    () -> findConsentAndVerifyAmount(idempotencyKey));

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

    // --- Общие шаги ---

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

    private UUID findConsentAndVerifyAmount(String idempotencyKey) {
        UUID uuid = dbClient.findConsentUuidByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new AssertionError(
                        "Idempotency key " + idempotencyKey + " not found in DB"));
        BigDecimal amountInDb = dbClient.findConsentAmount(uuid);
        Assert.assertEquals(amountInDb, AMOUNT_IN_DB,
                "Saved amount in DB mismatch for consent " + uuid);
        return uuid;
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
        ((ObjectNode) copy.getBody().at("/listPassportsConsentRequest/data/initiation")).put("amount", amount);
        return copy;
    }

    private Input copy(Input input) {
        JsonNode body = input.getBody().deepCopy();
        Input copy = new Input();
        copy.setContentType(input.getContentType());
        copy.setHeaders(input.getHeaders());
        copy.setBody(body);
        return copy;
    }
}
