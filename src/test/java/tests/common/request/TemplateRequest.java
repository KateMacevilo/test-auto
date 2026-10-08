package tests.common.request;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.Method;
import io.restassured.response.ValidatableResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tests.common.model.Input;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class TemplateRequest {

    private static final String SCOPE = "api-listpassportsconsent";
    private static final String CONSENT_PATH = "/api/paymentConsents/listPassports";

    public ValidatableResponse getHealth(BaseRequest baseRequest, String url) {
        String path = url + "/actuator/health";

        RequestSpecBuilder reqSpecBuilder = baseRequest.getDefaultRequestSpecification()
                .setUrlEncodingEnabled(false)
                .addHeader("Authorization", baseRequest.setAuth(SCOPE));

        ResponseSpecBuilder respSpecBuilder = baseRequest.getDefaultResponseSpecification();

        return baseRequest.sendRequest(reqSpecBuilder.build(), respSpecBuilder.build(), Method.GET, path);
    }

    public ValidatableResponse createPaymentConsent(Input input, BaseRequest baseRequest, String url) {
        String path = String.format("%s%s", url, CONSENT_PATH);

        RequestSpecBuilder reqSpecBuilder = baseRequest.getDefaultRequestSpecification()
                .addHeader("Authorization", baseRequest.setAuth(SCOPE))
                .setContentType(input.getContentType() != null ? input.getContentType() : "application/json;charset=UTF-8")
                .addHeaders(input.getHeaders())
                .setBody(input.getBody());

        ResponseSpecBuilder respSpecBuilder = baseRequest.getDefaultResponseSpecification();

        return baseRequest.sendRequest(reqSpecBuilder.build(), respSpecBuilder.build(), Method.POST, path);
    }

    /** GET согласия по id: GET /api/paymentConsents/listPassports/{path}. Тело не передаётся. */
    public ValidatableResponse getPaymentConsent(BaseRequest baseRequest, String url, String path,
                                                 Map<String, String> headers) {
        String fullPath = String.format("%s%s%s", url, CONSENT_PATH, path);

        RequestSpecBuilder reqSpecBuilder = baseRequest.getDefaultRequestSpecification()
                .addHeader("Authorization", baseRequest.setAuth(SCOPE))
                .addHeaders(headers);

        ResponseSpecBuilder respSpecBuilder = baseRequest.getDefaultResponseSpecification();

        return baseRequest.sendRequest(reqSpecBuilder.build(), respSpecBuilder.build(), Method.GET, fullPath);
    }
}
