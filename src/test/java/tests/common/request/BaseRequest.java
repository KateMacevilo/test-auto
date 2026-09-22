package tests.common.request;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.Method;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tests.common.utils.JwtAssertionUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;

@Slf4j
@Component
public class BaseRequest {

    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    @Value("${auth.url:}")
    private String authUrl;

    @Value("${auth.client-id:}")
    private String clientId;

    @Value("${auth.client-secret:}")
    private String clientSecret;

    @Value("${auth.static-token:}")
    private String staticToken;

    @Value("${jwt.user-id:}")
    private String userId;

    @Value("${jwt.subscriber:true}")
    private String subscriber;

    @Value("${jwt.user-categories:}")
    private String userCategories;

    @Value("${jwt.tax-number:}")
    private String taxNumber;

    @Value("${jwt.organization:}")
    private String organization;

    @Value("${jwt.application-uuid:}")
    private String applicationUuid;

    @Value("${jwt.key-type:}")
    private String keyType;

    @Value("${jwt.application-name:}")
    private String applicationName;

    public RequestSpecBuilder getDefaultRequestSpecification() {
        return new RequestSpecBuilder()
                .addHeader("Accept", "application/json")
                .addHeader("x-jwt-assertion", buildJwtAssertion());
    }

    public ResponseSpecBuilder getDefaultResponseSpecification() {
        return new ResponseSpecBuilder();
    }

    /**
     * Возвращает значение заголовка Authorization (Bearer ...) для заданного scope.
     * Если задан auth.static-token — он используется напрямую, иначе токен запрашивается
     * по auth.url через client credentials.
     * AGENTS.md: механизм получения токена — допущение, при расхождении править resolveToken.
     */
    public String setAuth(String scope) {
        return "Bearer " + tokenCache.computeIfAbsent(scope, s -> resolveToken(s));
    }

    public ValidatableResponse sendRequest(RequestSpecification requestSpecification,
                                           ResponseSpecification responseSpecification,
                                           Method method, String path) {
        return given()
                .spec(requestSpecification)
                .when()
                .request(method, path)
                .then()
                .spec(responseSpecification);
    }

    private String resolveToken(String scope) {
        if (staticToken != null && !staticToken.isBlank()) {
            return staticToken;
        }
        if (authUrl == null || authUrl.isBlank()) {
            throw new IllegalStateException(
                    "Neither auth.static-token nor auth.url is configured — cannot obtain Bearer token");
        }
        return given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "client_credentials")
                .formParam("client_id", clientId)
                .formParam("client_secret", clientSecret)
                .formParam("scope", scope)
                .when()
                .post(authUrl)
                .then()
                .log().ifError()
                .statusCode(200)
                .extract()
                .path("access_token");
    }

    private String buildJwtAssertion() {
        JwtAssertionUtil.JwtClaims claims = new JwtAssertionUtil.JwtClaims();
        claims.setUserId(userId);
        claims.setSubscriber(subscriber);
        claims.setUserCategories(userCategories);
        claims.setTaxNumber(taxNumber);
        claims.setOrganization(organization);
        claims.setApplicationUuid(applicationUuid);
        claims.setKeyType(keyType);
        claims.setApplicationName(applicationName);
        return JwtAssertionUtil.buildJwt(claims);
    }
}
