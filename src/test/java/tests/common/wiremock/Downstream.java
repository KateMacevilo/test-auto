package tests.common.wiremock;

import lombok.Getter;

/**
 * Даунстримы сервиса, заглушаемые через WireMock: для каждого — метод и путь заглушки,
 * ожидаемый статус и тело ответа. Имена enum-а используются в json кейсов (поле downstreams)
 * для выбора даунстримов сценария и задания их порядка; маппинг имени в заглушку — здесь,
 * в коде (переиспользуется между кейсами).
 */
@Getter
public enum Downstream {
    WSO2_AUTHORIZED_APPS("GET", "/25100/authorized-apps", 200,
            "{ \"total\": 1, \"list\": [] }"),
    SIGN_RULE_SERVICE("GET", "/25100", 200,
            "{ \"queryTypes\": [ { \"queryType\": 453, \"signGroupCodes\": [1, 2],"
                    + " \"numberRequired\": 2, \"numberMax\": 2 } ] }"),
    MGT_APIKEY_BINDINGS("POST", "/bindings", 200, "{ }");

    private final String method;
    private final String urlPath;
    private final int status;
    private final String body;

    Downstream(String method, String urlPath, int status, String body) {
        this.method = method;
        this.urlPath = urlPath;
        this.status = status;
        this.body = body;
    }
}
