package tests.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@UtilityClass
public class JwtAssertionUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Формирует unsigned JWT (header.payload.signature) для заголовка x-jwt-assertion.
     * Подпись не проверяется сервисом (см. реализацию parseJwt в сервисе) — payload кодируется в Base64URL.
     * AGENTS.md: состав claims и отсутствие подписи — допущение по скриншоту сервиса.
     */
    public static String buildJwt(JwtClaims claims) {
        try {
            Map<String, Object> header = Map.of("alg", "none", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", claims.getUserId());
            payload.put("subscriber", claims.getSubscriber());
            payload.put("userCategories", claims.getUserCategories());
            payload.put("taxNumber", claims.getTaxNumber());
            payload.put("organization", claims.getOrganization());
            payload.put("applicationUuid", claims.getApplicationUuid());
            payload.put("keyType", claims.getKeyType());
            payload.put("applicationName", claims.getApplicationName());
            return base64Url(MAPPER.writeValueAsBytes(header)) + "."
                    + base64Url(MAPPER.writeValueAsBytes(payload)) + ".";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot build x-jwt-assertion", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @lombok.Data
    public static class JwtClaims {
        private String userId;
        private String subscriber;
        private String userCategories;
        private String taxNumber;
        private String organization;
        private String applicationUuid;
        private String keyType;
        private String applicationName;
    }
}
