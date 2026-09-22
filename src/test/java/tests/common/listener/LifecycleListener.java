package tests.common.listener;

import lombok.extern.slf4j.Slf4j;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ISuiteResult;
import org.testng.ITestContext;

@Slf4j
public class LifecycleListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        log.info("Suite started: {}", suite.getName());
    }

    @Override
    public void onFinish(ISuite suite) {
        int passed = 0;
        int failed = 0;
        int skipped = 0;
        for (ISuiteResult suiteResult : suite.getResults().values()) {
            ITestContext context = suiteResult.getTestContext();
            passed += context.getPassedTests().size();
            failed += context.getFailedTests().size();
            skipped += context.getSkippedTests().size();
        }
        log.info("Suite finished: {}, passed: {}, failed: {}, skipped: {}",
                suite.getName(), passed, failed, skipped);
    }
}
