package tests;

import io.qameta.allure.Link;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;
import tests.common.AbstractApiTest;
import tests.common.assertions.Assertions;
import tests.common.dataprovider.DataProviders;
import tests.common.model.TestData;

@Slf4j
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
    @Test(dataProvider = "MainDP", dataProviderClass = DataProviders.class, priority = 2)
    public void createPaymentConsent(TestData testData) {
        Response response = templateRequest.createPaymentConsent(testData.getInput(), baseRequest, url).extract().response();
        Assertions.verifyStatusCode(response, testData.getExpected().getStatusCode());
        verifyConsentSchema(response);
        Assertions.verifyResponseParam(response, testData.getExpected().getParams());
    }
}
