package tests.common.model;

import lombok.Data;

import java.util.List;

@Data
public class TestData {
    /** ID тест-кейса в Allure (TestOps), например "24188" */
    private String id;
    private String name;
    private String description;
    private Input input;
    private Expected expected;
    /**
     * Заглушки WireMock в нативном формате маппингов, выставляются перед запросом
     * к сервису и сбрасываются после (см. AbstractApiTest.wireMockStubs).
     */
    private List<Object> wiremock;
}
