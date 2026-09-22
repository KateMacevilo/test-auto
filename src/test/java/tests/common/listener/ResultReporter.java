package tests.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.testng.ITestListener;
import org.testng.ITestResult;

@Slf4j
public class ResultReporter implements ITestListener {

    @Override
    public void onTestSuccess(ITestResult result) {
        log.info("PASSED: {}.{} [{} ms]", result.getTestClass().getName(), result.getName(),
                result.getEndMillis() - result.getStartMillis());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        log.error("FAILED: {}.{}", result.getTestClass().getName(), result.getName(), result.getThrowable());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        log.warn("SKIPPED: {}.{}", result.getTestClass().getName(), result.getName());
    }
}
