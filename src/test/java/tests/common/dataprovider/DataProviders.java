package tests.common.dataprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.DataProvider;
import tests.common.model.TestData;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;

public class DataProviders {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TESTDATA_DIR = "testdata/";

    /**
     * Тестовые данные читаются из classpath:testdata/<имя тестового метода>.json.
     * Файл может содержать один объект TestData или массив объектов.
     */
    @DataProvider(name = "MainDP")
    public static Object[][] mainDP(Method method) throws IOException {
        String resource = TESTDATA_DIR + method.getName() + ".json";
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
