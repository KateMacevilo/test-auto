package tests.common.dataprovider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import lombok.experimental.UtilityClass;
import tests.common.model.SuppliedTestData;
import tests.common.model.TestData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AGENTS.md: класс восстановлен дословно по скриншоту из рабочего проекта — НЕ ИЗМЕНЯТЬ.
 * Зависит от классов-заглушек (Matchers, SuppliedTestData, DataSupplier, JacksonTreeFactory) —
 * при переносе в рабочий проект заменить на оригинальные.
 */
@UtilityClass
public class JSONReader {

    private static final DateFormatter dateFormatter = new DateFormatter();

    public static <T> Iterator<Object[]> getTestData(String path, String methodeName) throws IOException {
        Class<Matchers> clazz = Matchers.class;
        try (InputStream inputStream = clazz.getResourceAsStream(path)) {
            if (inputStream != null) {
                String json = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                JsonObject jsonObject = new JsonParser().parse(json).getAsJsonObject();
                String parsedJson = jsonObject.get(methodeName).toString();
                parsedJson = dateFormatter.changeString(parsedJson);
                List<T> objects = new Gson().fromJson(parsedJson, new TypeToken<List<TestData>>() {
                }.getType());
                return objects.stream().map(g -> new Object[]{g}).collect(Collectors.toList()).iterator();
            }
        }
        return null;
    }

    public static <T> Iterator<Object[]> getTestDataFile(String methodeName) throws IOException {
        Class<Matchers> clazz = Matchers.class;
        try (InputStream inputStream = clazz.getResourceAsStream("/" + methodeName)) {
            if (inputStream != null) {
                String json = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                List<T> objects = new Gson().fromJson(dateFormatter.changeString(json), new TypeToken<List<TestData>>() {
                }.getType());
                return objects.stream().map(g -> new Object[]{g}).collect(Collectors.toList()).iterator();
            }
        }
        return null;
    }

    public static Iterator<Object[]> getSuppliedTestDataFile(String methodeName) throws IOException {
        Class<Matchers> clazz = Matchers.class;
        try (InputStream inputStream = clazz.getResourceAsStream("/" + methodeName + "_supplied.json")) {
            if (inputStream != null) {
                String json = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                List<SuppliedTestData> objects = new ObjectMapper().readValue(json,
                        new TypeReference<>() {
                        });
                DataSupplier<JacksonTreeFactory> supplier = new DataSupplier<>(new JacksonTreeFactory());
                return objects.stream()
                        .map(supplier::populateToTestData)
                        .flatMap(List::stream)
                        .map(g -> new Object[]{g})
                        .collect(Collectors.toList()).iterator();
            }
        }
        return null;
    }
}
