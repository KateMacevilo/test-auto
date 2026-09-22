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
