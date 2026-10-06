package tests.common.dataprovider;

import lombok.experimental.UtilityClass;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AGENTS.md: методы getTestData / getTestDataForKafka / getTestDataFile / getSuppliedTestDataFile
 * восстановлены дословно по скриншоту из рабочего проекта — НЕ ИЗМЕНЯТЬ.
 * Доработанный/добавленный метод — только allFilesDP (см. ниже).
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

    private static final String API_CASES_DIR = "testdata/api/";
    private static final String WIREMOCK_CASES_DIR = "testdata/wiremock/";

    /** Резолвим файлы через classpath — как и остальные провайдеры (getResourceAsStream), а не через файловую систему. */
    private static final ResourcePatternResolver RESOURCE_RESOLVER =
            new PathMatchingResourcePatternResolver(DataProviders.class.getClassLoader());

    /**
     * Все тест-кейсы из всех json-файлов каталога testdata/api/ (каждый файл — массив
     * объектов TestData). Имена файлов не привязаны к именам методов. Файлы читаются
     * в отсортированном порядке — детерминированный порядок прогона.
     * Пустой каталог или нечитаемый файл — ошибка прогона (а не тихий skip с пустыми данными).
     */
    @DataProvider(name = "AllFilesDP")
    public static Iterator<Object[]> allFilesDP() throws IOException {
        return allCasesFrom(API_CASES_DIR);
    }

    /**
     * Доработка: кейсы с заглушками WireMock из testdata/wiremock/ — отдельный пакет
     * для метода Tests.createConsentWithWireMock. Формат файлов тот же, что у AllFilesDP.
     */
    @DataProvider(name = "WireMockDP")
    public static Iterator<Object[]> wireMockDP() throws IOException {
        return allCasesFrom(WIREMOCK_CASES_DIR);
    }

    private static Iterator<Object[]> allCasesFrom(String dir) throws IOException {
        List<String> fileNames = listJsonFiles(dir);
        if (fileNames.isEmpty()) {
            throw new IllegalStateException("No test case files (*.json) found in classpath:/" + dir);
        }
        List<Object[]> allCases = new ArrayList<>();
        for (String fileName : fileNames) {
            Iterator<Object[]> rows = JSONReader.getTestDataFile(dir + fileName);
            if (rows == null) {
                throw new IllegalStateException("Failed to read test data file: /" + dir + fileName);
            }
            rows.forEachRemaining(allCases::add);
        }
        return allCases.iterator();
    }

    private static List<String> listJsonFiles(String dir) throws IOException {
        return Arrays.stream(RESOURCE_RESOLVER.getResources("classpath*:/" + dir + "*.json"))
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }
}
