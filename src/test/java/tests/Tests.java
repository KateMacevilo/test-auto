package tests;

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
import tests.common.model.TestData;

/**
 * Универсальный прогон тест-кейсов: каждый кейс — отдельный json-файл в testdata/api/
 * (файл может содержать массив параметризаций). Все кейсы сводятся к схеме
 * «один запрос → один ответ»: POST, проверка статуса/схемы/параметров, проверка БД.
 * Сценарии с состоянием (несколько зависимых запросов, идемпотентность) — в CreatePaymentConsentTest.
 */
@Slf4j
@Feature("Создание согласия на инициирование платежа")
public class Tests extends AbstractApiTest {

    private static final String CONFLUENCE = "https://confluence.priorbank.by:8443/display/API/prior-ob-svc-api-listpassportsconsent";

    @Link(name = "prior-ob-svc-api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Health check")
    @Test(dataProvider = "MainDP", dataProviderClass = DataProviders.class, priority = 1)
    public void getHealth(TestData testData) {
        Response response = templateRequest.getHealth(baseRequest, url).extract().response();
        Assertions.verifyStatusCode(response, testData.getExpected().getStatusCode());
        Assertions.verifyResponseSchema(response, HEALTH_SCHEMA);
        Assertions.verifyResponseParam(response, testData.getExpected().getParams());
    }

    @Link(name = "prior-ob-svc-api-listpassportsconsent [Confluence]", url = CONFLUENCE)
    @Story("Создание согласия (listPassports)")
    @Test(dataProvider = "AllFilesDP", dataProviderClass = DataProviders.class, priority = 2)
    public void createConsent(TestData testData) {
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

    /**
     * Проверка БД по x-idempotency-key кейса:
     *  - негатив (4xx/5xx) — записей в БД создано быть не должно;
     *  - позитив (2xx) — созданные тестом записи удаляются (очистка).
     */
    private void verifyDbState(TestData testData) {
        String idempotencyKey = testData.getInput().getHeaders() != null
                ? testData.getInput().getHeaders().get("x-idempotency-key")
                : null;
        if (idempotencyKey == null) {
            return;
        }
        if (testData.getExpected().getStatusCode() < 300) {
            dbClient.findConsentUuidByIdempotencyKey(idempotencyKey)
                    .ifPresent(dbClient::deleteAllDataConsentById);
        } else {
            Assert.assertTrue(dbClient.findConsentUuidByIdempotencyKey(idempotencyKey).isEmpty(),
                    "No consent records expected in DB for idempotency key " + idempotencyKey);
        }
    }
}
