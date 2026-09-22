package tests.common.assertions;

import io.restassured.module.jsv.JsonSchemaValidator;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;

import java.util.Map;

import static io.restassured.module.jsv.JsonSchemaValidatorSettings.settings;

@Slf4j
public class Assertions {

    public static void verifyStatusCode(Response response, int expectedStatusCode) {
        int actual = response.getStatusCode();
        Assert.assertEquals(actual, expectedStatusCode,
                String.format("Unexpected status code. Response: %s", response.getBody().asPrettyString()));
    }

    public static void verifyResponseSchema(Response response, String path) {
        response.then().assertThat().body(JsonSchemaValidator
                .matchesJsonSchemaInClasspath(path)
                .using(settings().with().checkedValidation(false)));
    }

    public static void verifyResponseParam(Response response, Map<String, Object> params) {
        if (params == null) {
            return;
        }
        params.forEach((path, expected) -> {
            Object actual = response.jsonPath().get(path);
            Assert.assertEquals(String.valueOf(actual), String.valueOf(expected),
                    String.format("Param '%s' mismatch. Response: %s", path, response.getBody().asPrettyString()));
        });
    }
}
