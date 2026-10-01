package tests.common;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Listeners;
import tests.common.assertions.Assertions;
import tests.common.config.TestConfig;
import tests.common.db.DbClient;
import tests.common.listener.AllureTestCaseListener;
import tests.common.listener.LifecycleListener;
import tests.common.listener.ResultReporter;
import tests.common.request.BaseRequest;
import tests.common.request.TemplateRequest;
import tests.common.model.TestData;
import tests.common.wiremock.WireMockClient;

/**
 * Базовый класс API-тестов: поднимает Spring-контекст, инжектит общие бины
 * и содержит шаред-хелперы. Новый тестовый класс наследуется от него и
 * объявляет только сами тестовые методы.
 */
@SpringBootTest(classes = TestConfig.class)
@Slf4j
@Listeners({ResultReporter.class, LifecycleListener.class, AllureTestCaseListener.class})
public abstract class AbstractApiTest extends AbstractTestNGSpringContextTests {

    protected static final String CONSENT_SCHEMA = "schemas/listPassportsConsent-schema.json";
    protected static final String HEALTH_SCHEMA = "schemas/health-schema.json";

    protected final TemplateRequest templateRequest = new TemplateRequest();

    @Value("${prior.suite.common.autotest.url}")
    protected String url;

    protected BaseRequest baseRequest;
    protected DbClient dbClient;
    protected WireMockClient wireMockClient;

    @Autowired
    public void setWireMockClient(WireMockClient wireMockClient) {
        this.wireMockClient = wireMockClient;
    }

    /**
     * Выставляет заглушки WireMock из кейса (поле wiremock) и возвращает closeable,
     * который сбрасывает маппинги. Использовать в try-with-resources вокруг запроса:
     * try (AutoCloseable ignored = wireMockStubs(testData)) { ... }
     * Если у кейса нет wiremock — no-op, ничего не выставляет и не сбрасывает.
     */
    protected AutoCloseable wireMockStubs(TestData testData) {
        if (testData.getWiremock() == null || testData.getWiremock().isEmpty()) {
            return () -> { };
        }
        wireMockClient.uploadStubs(testData.getWiremock());
        return wireMockClient::resetMappings;
    }

    @Autowired
    public void setBaseRequest(BaseRequest baseRequest) {
        this.baseRequest = baseRequest;
    }

    @Autowired
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected void verifyConsentSchema(Response response) {
        Assertions.verifyResponseSchema(response, CONSENT_SCHEMA);
    }
}
