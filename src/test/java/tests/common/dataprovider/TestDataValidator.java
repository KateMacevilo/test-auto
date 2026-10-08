package tests.common.dataprovider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tests.common.model.DbState;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Валидация json тест-кейсов ДО десериализации Gson'ом.
 *
 * AGENTS.md: Gson (JSONReader — дословная копия из рабочего проекта, не меняется) молча
 * игнорирует неизвестные поля — опечатка в ключе ("locl" вместо "local") дала бы кейсу
 * неправильное поведение без единой ошибки. Поэтому каждый файл с кейсами проходит
 * здесь проверку: только известные ключи на каждом уровне + базовые проверки типов.
 * Неизвестный ключ — падение с указанием файла и пути — до старта прогона.
 * Валидация подключена в наших провайдерах (allFilesDP/wiremockDP) и вручную
 * для FileDP-методов (Tests вызывает validateResource) — сам JSONReader не трогаем.
 */
public final class TestDataValidator {

    private static final Set<String> TEST_DATA_KEYS =
            Set.of("id", "name", "description", "local", "input", "expected", "downstreams", "dbSetup");
    private static final Set<String> INPUT_KEYS = Set.of("contentType", "headers", "body");
    private static final Set<String> EXPECTED_KEYS =
            Set.of("statusCode", "params", "verifySchema", "dbState", "dbParams");
    private static final Set<String> DB_TABLE_KEYS = Set.of("table", "rows");
    private static final Set<String> DB_SETUP_KEYS = Set.of("table", "columns");
    private static final Set<String> DOWNSTREAM_KEYS =
            Set.of("name", "method", "urlPath", "status", "body", "section", "queryParams", "headers");
    private static final Set<String> DB_STATES =
            java.util.Arrays.stream(DbState.values()).map(Enum::name).collect(Collectors.toSet());

    private TestDataValidator() {
    }

    /**
     * Читает ресурс (classpath, путь без ведущего '/') и валидирует.
     * methodKey != null — ресурс объект вида {имяМетода: [кейсы]} (формат getTestData/FileDP
     * для файла с несколькими методами); null — ресурс сам массив кейсов.
     */
    public static void validateResource(String resourcePath, String methodKey) {
        String json;
        try (var stream = TestDataValidator.class.getResourceAsStream("/" + resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Test case resource not found: /" + resourcePath);
            }
            json = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read test case resource: /" + resourcePath, e);
        }
        validate(json, methodKey, resourcePath);
    }

    public static void validate(String json, String methodKey, String source) {
        JsonElement root = JsonParser.parseString(json);
        JsonElement cases = root;
        if (methodKey != null) {
            if (!root.isJsonObject() || !root.getAsJsonObject().has(methodKey)) {
                throw error(source, "$", "no key '" + methodKey + "' with test cases");
            }
            cases = root.getAsJsonObject().get(methodKey);
        }
        if (!cases.isJsonArray() || cases.getAsJsonArray().isEmpty()) {
            throw error(source, "$", "expected a non-empty array of test cases");
        }
        int[] index = {0};
        cases.getAsJsonArray().forEach(element -> validateCase(element, source, "$[" + index[0]++ + "]"));
    }

    private static void validateCase(JsonElement element, String source, String path) {
        requireObject(element, source, path);
        JsonObject testData = element.getAsJsonObject();
        checkKeys(testData, TEST_DATA_KEYS, source, path);
        checkString(testData, "id", source, path);
        checkString(testData, "name", source, path);
        if (testData.has("local") && !testData.get("local").isJsonPrimitive()) {
            throw error(source, path + ".local", "expected boolean");
        }
        if (testData.has("input")) {
            JsonObject input = requireObject(testData.get("input"), source, path + ".input");
            checkKeys(input, INPUT_KEYS, source, path + ".input");
        }
        if (testData.has("expected")) {
            validateExpected(testData.get("expected"), source, path + ".expected");
        }
        if (testData.has("downstreams")) {
            validateDownstreams(testData.get("downstreams"), source, path + ".downstreams");
        }
        if (testData.has("dbSetup")) {
            if (!testData.get("dbSetup").isJsonArray()) {
                throw error(source, path + ".dbSetup", "expected array of {table, columns} objects");
            }
            int[] i = {0};
            testData.getAsJsonArray("dbSetup").forEach(setupElement -> {
                String setupPath = path + ".dbSetup[" + i[0]++ + "]";
                JsonObject setup = requireObject(setupElement, source, setupPath);
                checkKeys(setup, DB_SETUP_KEYS, source, setupPath);
                if (!setup.has("table") || !setup.get("table").isJsonPrimitive()) {
                    throw error(source, setupPath + ".table", "expected string — обязательное поле");
                }
                if (!setup.has("columns") || !setup.get("columns").isJsonObject()) {
                    throw error(source, setupPath + ".columns", "expected {column: value} object — обязательное поле");
                }
            });
        }
    }

    private static void validateExpected(JsonElement element, String source, String path) {
        JsonObject expected = requireObject(element, source, path);
        checkKeys(expected, EXPECTED_KEYS, source, path);
        if (!expected.has("statusCode") || !expected.get("statusCode").isJsonPrimitive()
                || !expected.get("statusCode").getAsJsonPrimitive().isNumber()) {
            throw error(source, path + ".statusCode", "expected number — обязательное поле");
        }
        if (expected.has("dbState") && !DB_STATES.contains(expected.get("dbState").getAsString())) {
            throw error(source, path + ".dbState", "expected one of " + DB_STATES
                    + ", but was '" + expected.get("dbState").getAsString() + "'");
        }
        if (expected.has("dbParams")) {
            int[] i = {0};
            expected.getAsJsonArray("dbParams").forEach(tableElement -> {
                String tablePath = path + ".dbParams[" + i[0]++ + "]";
                JsonObject table = requireObject(tableElement, source, tablePath);
                checkKeys(table, DB_TABLE_KEYS, source, tablePath);
                requireStringMapArray(table, "rows", source, tablePath);
            });
        }
    }

    private static void validateDownstreams(JsonElement element, String source, String path) {
        int[] i = {0};
        element.getAsJsonArray().forEach(downstreamElement -> {
            String downstreamPath = path + "[" + i[0]++ + "]";
            JsonObject downstream = requireObject(downstreamElement, source, downstreamPath);
            checkKeys(downstream, DOWNSTREAM_KEYS, source, downstreamPath);
            if (!downstream.has("method") || !downstream.get("method").isJsonPrimitive()) {
                throw error(source, downstreamPath + ".method", "expected string — обязательное поле");
            }
            if (!downstream.has("urlPath") || !downstream.get("urlPath").isJsonPrimitive()) {
                throw error(source, downstreamPath + ".urlPath", "expected string — обязательное поле");
            }
            if (downstream.has("status") && !downstream.get("status").getAsJsonPrimitive().isNumber()) {
                throw error(source, downstreamPath + ".status", "expected number");
            }
            if (downstream.has("queryParams")) {
                requireStringMap(downstream, "queryParams", source, downstreamPath);
            }
            if (downstream.has("headers")) {
                requireStringMap(downstream, "headers", source, downstreamPath);
            }
        });
    }

    /** Неизвестные ключи объекта — падение: Gson бы их молча выбросил, кейс работал бы не так. */
    private static void checkKeys(JsonObject object, Set<String> allowed, String source, String path) {
        Set<String> unknown = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                unknown.add(entry.getKey());
            }
        }
        if (!unknown.isEmpty()) {
            throw error(source, path, "unknown fields " + unknown
                    + " — опечатка? Разрешены только: " + allowed);
        }
    }

    private static void checkString(JsonObject object, String key, String source, String path) {
        if (object.has(key) && !object.get(key).isJsonPrimitive()) {
            throw error(source, path + "." + key, "expected string");
        }
    }

    private static void requireStringMap(JsonObject object, String key, String source, String path) {
        JsonObject map = requireObject(object.get(key), source, path + "." + key);
        for (Map.Entry<String, JsonElement> entry : map.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                throw error(source, path + "." + key + "." + entry.getKey(), "expected string value");
            }
        }
    }

    private static void requireStringMapArray(JsonObject object, String key, String source, String path) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            throw error(source, path + "." + key, "expected array of {column: value} objects");
        }
        int[] i = {0};
        object.getAsJsonArray(key).forEach(row -> {
            JsonObject rowObject = requireObject(row, source, path + "." + key + "[" + i[0]++ + "]");
            for (Map.Entry<String, JsonElement> entry : rowObject.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) {
                    throw error(source, path + "." + key + "." + entry.getKey(), "expected string value");
                }
            }
        });
    }

    private static JsonObject requireObject(JsonElement element, String source, String path) {
        if (element == null || !element.isJsonObject()) {
            throw error(source, path, "expected object");
        }
        return element.getAsJsonObject();
    }

    private static IllegalArgumentException error(String source, String path, String detail) {
        return new IllegalArgumentException(
                "Invalid test case in '" + source + "' at " + path + ": " + detail);
    }
}
