package tests.common.model;

import lombok.Data;
import tests.common.wiremock.Downstream;
import tests.common.wiremock.StubResponse;

import java.util.List;
import java.util.Map;

@Data
public class TestData {
    /** ID тест-кейса в Allure (TestOps), например "24188" */
    private String id;
    private String name;
    private String description;
    private Input input;
    private Expected expected;
    /**
     * Кейс предназначен только для локального прогона (напр. зависит от WireMock):
     * при wiremock.cases.enabled=false такой кейс скипается.
     */
    private boolean local;
    /**
     * Даунстримы сценария для WireMock-кейсов: имена enum {@link Downstream} в порядке
     * предпроверки/проверки. Не задано — предполагаются все даунстримы реестра.
     */
    private List<Downstream> downstreams;
    /**
     * Переопределения ответов заглушек: ключ — имя enum {@link Downstream}, значение —
     * статус/тело ответа для этого кейса. Не задано — используются дефолты даунстримов.
     */
    private Map<String, StubResponse> stubResponses;
}
