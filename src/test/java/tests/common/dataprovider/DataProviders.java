package tests.common.dataprovider;

import lombok.experimental.UtilityClass;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AGENTS.md: методы getTestData / getTestDataForKafka / getTestDataFile / getSuppliedTestDataFile
 * восстановлены дословно по скриншоту из рабочего проекта — НЕ ИЗМЕНЯТЬ.
 * Добавленные методы — allFilesDP (фильтр файлов по имени вызывающего метода) / wireMockDP (см. ниже).
 */
@UtilityClass
public class DataProviders {

    @DataProvider(name = "MainDP")
    public static Iterator<Object[]> getTestData(Method method) throws IOException {
        // TODO: 18.11.2024 разделить на методы???
        return JSONReader.getTestData("/testData.json", method.getName());
    }

    @DataProvider(name = "MainDPForKafka")
    public static Iterator<Object[]> getTestDataForKafka(Method method) throws IOException {
        return JSONReaderForKafka.getInputAndOutputMessage("/inputAndOutputMessage.json", method.getName());
    }

    /* Вычитка данных из файла целиком, где имя файла = имя метода с аннотацией.
    Файл - массив объектов TestData.class в формате JSON без расширения*/
    @DataProvider(name = "FileDP")
    public static Iterator<Object[]> getTestDataFile(Method method) throws IOException {
        return JSONReader.getTestDataFile(method.getName());
    }

    @DataProvider(name = "SuppliedFileDP")
    public static Iterator<Object[]> getSuppliedTestDataFile(Method method) throws IOException {
        return JSONReader.getSuppliedTestDataFile(method.getName());
    }

    // ===== Доработка: прогон всех кейсов каталога одним универсальным методом =====

    private static final String CASES_DIR = "testdata/";
    private static final String WIREMOCK_CASES_DIR = "testdata/wiremock/";

    /** Резолвим файлы через classpath — как и остальные провайдеры (getResourceAsStream), а не через файловую систему. */
    private static final ResourcePatternResolver RESOURCE_RESOLVER =
            new PathMatchingResourcePatternResolver(DataProviders.class.getClassLoader());

    /**
     * Все тест-кейсы из json-файлов каталога testdata/, относящиеся к вызывающему методу:
     * имя файла должно начинаться с имени метода в lower_snake (createConsent → create_consent*.json,
     * getConsent → get_consent*.json). Каждый файл — массив объектов TestData, файлы читаются
     * в отсортированном порядке — детерминированный порядок прогона.
     * Отсутствие файлов под метод или нечитаемый файл — ошибка прогона (а не тихий skip с пустыми данными).
     */
    @DataProvider(name = "AllFilesDP")
    public static Iterator<Object[]> allFilesDP(Method method) throws IOException {
        return allCasesFrom(CASES_DIR, toSnakeCase(method.getName()));
    }

    /**
     * Доработка: кейсы с заглушками WireMock из testdata/wiremock/ — отдельный пакет
     * для метода Tests.createConsentWithWireMock. Формат файлов тот же, что у AllFilesDP.
     */
    @DataProvider(name = "WireMockDP")
    public static Iterator<Object[]> wireMockDP() throws IOException {
        return allCasesFrom(WIREMOCK_CASES_DIR, null);
    }

    private static String toSnakeCase(String methodName) {
        return methodName.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private static Iterator<Object[]> allCasesFrom(String dir, String filePrefix) throws IOException {
        List<Resource> resources = Arrays.stream(
                        RESOURCE_RESOLVER.getResources("classpath*:/" + dir + "*.json"))
                .filter(resource -> resource.getFilename() != null)
                .filter(resource -> filePrefix == null || resource.getFilename().startsWith(filePrefix))
                .sorted(Comparator.comparing(Resource::getFilename))
                .collect(Collectors.toList());
        if (resources.isEmpty()) {
            throw new IllegalStateException("No test case files (*.json) found in classpath:/" + dir
                    + (filePrefix != null ? " for method prefix '" + filePrefix + "'" : ""));
        }
        List<Object[]> allCases = new ArrayList<>();
        for (Resource resource : resources) {
            String fileName = dir + resource.getFilename();
            // валидация ДО десериализации: Gson молча игнорирует неизвестные поля —
            // опечатка в ключе дала бы тихи неправильный прогон (см. TestDataValidator)
            String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            TestDataValidator.validate(raw, null, fileName);
            Iterator<Object[]> rows = JSONReader.getTestDataFile(fileName);
            if (rows == null) {
                throw new IllegalStateException("Failed to read test data file: /" + fileName);
            }
            rows.forEachRemaining(allCases::add);
        }
        return allCases.iterator();
    }
}
