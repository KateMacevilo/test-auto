package tests.common.dataprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.DataProvider;
import tests.common.model.TestData;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DataProviders {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TESTDATA_DIR = "testdata/";

    /**
     * Тестовые данные читаются из classpath:testdata/<имя тестового метода>.json.
     * Файл может содержать один объект TestData или массив объектов.
     */
@DataProvider(name = "MainDP")
    public static Object[][] mainDP(Method method) throws IOException {
        return loadFile(TESTDATA_DIR + method.getName() + ".json");
    }

    /**
     * Вычитка всех тест-кейсов из всех json-файлов каталога testdata/api/
     * (каждый файл — один объект TestData или массив объектов).
     * Позволяет держать много кейсов в отдельных небольших файлах и прогонять
     * их одним универсальным методом.
     */
    @DataProvider(name = "AllFilesDP")
    public static Object[][] allFilesDP() throws IOException, URISyntaxException {
        URL dirUrl = DataProviders.class.getClassLoader().getResource(TESTDATA_DIR + "api");
        if (dirUrl == null) {
            throw new IllegalStateException("Test data directory not found: " + TESTDATA_DIR + "api");
        }
        List<Object[]> rows = new ArrayList<>();
        try (Stream<Path> paths = Files.list(Paths.get(dirUrl.toURI()))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                rows.addAll(List.of(loadFile(TESTDATA_DIR + "api/" + path.getFileName())));
            }
        }
        return rows.toArray(new Object[0][]);
    }

    private static Object[][] loadFile(String resource) throws IOException {
        try (InputStream is = DataProviders.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException("Test data file not found: " + resource);
            }
            List<TestData> testData = MAPPER.readValue(is,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, TestData.class));
            Object[][] result = new Object[testData.size()][1];
            for (int i = 0; i < testData.size(); i++) {
                result[i][0] = testData.get(i);
            }
            return result;
        }
    }
}
