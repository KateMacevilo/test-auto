package tests.common.listener;

import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.model.Label;
import lombok.extern.slf4j.Slf4j;
import org.testng.ITestListener;
import org.testng.ITestResult;
import tests.common.model.TestData;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Переносит метаданные тест-кейса из TestData в отчёт Allure:
 *  - имя теста из testData.name (вместо имени метода),
 *  - метка ALLURE_ID из testData.id — для связи автотеста с тест-кейсом в Allure TestOps,
 *  - описание из testData.description,
 *  - параметры датапровайдера не пишутся в отчёт.
 */
@Slf4j
public class AllureTestCaseListener implements ITestListener {

    private static final String ALLURE_ID_LABEL = "ALLURE_ID";

    @Override
    public void onTestStart(ITestResult result) {
        extractTestData(result).ifPresent(testData -> {
            AllureLifecycle lifecycle = Allure.getLifecycle();
            lifecycle.updateTestCase(testResult -> {
                if (testData.getName() != null && !testData.getName().isBlank()) {
                    testResult.setName(testData.getName());
                }
                if (testData.getDescription() != null && !testData.getDescription().isBlank()) {
                    testResult.setDescription(testData.getDescription());
                }
                testResult.setParameters(new ArrayList<>());
                if (testData.getId() != null && !testData.getId().isBlank()) {
                    testResult.getLabels().add(new Label()
                            .setName(ALLURE_ID_LABEL)
                            .setValue(testData.getId()));
                }
            });
        });
    }

    private java.util.Optional<TestData> extractTestData(ITestResult result) {
        return Arrays.stream(result.getParameters())
                .filter(TestData.class::isInstance)
                .map(TestData.class::cast)
                .findFirst();
    }
}
