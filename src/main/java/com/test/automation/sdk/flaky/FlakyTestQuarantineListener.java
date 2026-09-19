package com.test.automation.sdk.flaky;

import com.test.automation.sdk.config.ConfigurationManager;
import com.test.automation.sdk.reporting.ExecutionReporting;
import com.test.automation.sdk.testbase.TestBase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IClass;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Opt-in flaky-test quarantine (declare in your TestNG suite XML alongside
 * {@code com.test.automation.sdk.listener.Listener}, after it, so the
 * genuine failure is still recorded in reports/analytics before this
 * listener reclassifies the final TestNG result -- see SDK-USER-GUIDE.md
 * "Retry & Listeners").
 *
 * <p>When a test ultimately fails (after any {@code Retry}/{@code IRetryAnalyzer}
 * retries within this same run have been exhausted), this listener checks
 * whether the cross-run analytics history ({@link FlakyTestRegistry})
 * classifies that exact test as "known flaky". If it does -- and
 * {@code flaky.quarantine.enabled} is turned on -- the TestNG result is
 * reclassified from FAILED to SKIPPED so a single intermittent test does not
 * block an otherwise-healthy build, while a clear {@code TEST_QUARANTINED}
 * warning is still emitted through {@link ExecutionReporting} so the
 * quarantine is visible, not silent. A test that is failing for the first
 * time (no history) or that fails almost every run (broken, not flaky) is
 * never reclassified -- it fails the build normally, exactly as before this
 * feature existed.</p>
 */
public class FlakyTestQuarantineListener implements ITestListener {

    public static final Logger log = LogManager.getLogger(FlakyTestQuarantineListener.class.getName());

    private volatile FlakyTestRegistry registry;

    @Override
    public void onTestFailure(ITestResult result) {
        ConfigurationManager.FlakyQuarantineConfig config = ConfigurationManager.getFlakyQuarantineConfig();
        if (!config.enabled()) {
            return;
        }

        String testKey = buildTestKey(result);
        if (testKey.isEmpty()) {
            return;
        }

        FlakyTestRegistry registry = getOrLoadRegistry(config);
        if (!registry.isKnownFlaky(testKey)) {
            return;
        }

        String detail = "Known flaky test '" + testKey + "' failed -- quarantined (reclassified as SKIP) "
                + "based on cross-run analytics history; see test-output/analytics for the full history.";
        log.warn("[FlakyTestQuarantineListener] {}", detail);
        Object owner = result.getInstance();
        ExecutionReporting.warning(owner instanceof TestBase ? (TestBase) owner : null, detail);

        reclassifyAsSkipped(result);
    }

    private FlakyTestRegistry getOrLoadRegistry(ConfigurationManager.FlakyQuarantineConfig config) {
        FlakyTestRegistry local = registry;
        if (local == null) {
            synchronized (this) {
                local = registry;
                if (local == null) {
                    Path analyticsDirectory = Paths.get(config.analyticsDirectory());
                    local = new FlakyTestRegistry(analyticsDirectory, config.minRunsForQuarantine(),
                            config.maxFailureRatePercent());
                    registry = local;
                }
            }
        }
        return local;
    }

    private static void reclassifyAsSkipped(ITestResult result) {
        result.setStatus(ITestResult.SKIP);
        ITestContext context = result.getTestContext();
        if (context == null) {
            return;
        }
        try {
            context.getFailedTests().removeResult(result);
            context.getSkippedTests().addResult(result);
        } catch (Exception e) {
            log.warn("[FlakyTestQuarantineListener] Could not move quarantined result between TestNG result sets", e);
        }
    }

    private static String buildTestKey(ITestResult result) {
        String className = resolveClassName(result);
        String methodName = resolveMethodName(result);
        String testCaseName = TestBase.getCurrentTestCaseName();
        return FlakyTestRegistry.buildTestKey(className, methodName, testCaseName);
    }

    private static String resolveClassName(ITestResult result) {
        IClass testClass = result.getTestClass();
        if (testClass == null) {
            return "";
        }
        Class<?> realClass = testClass.getRealClass();
        return realClass != null ? realClass.getSimpleName() : "";
    }

    private static String resolveMethodName(ITestResult result) {
        ITestNGMethod method = result.getMethod();
        if (method != null && method.getMethodName() != null) {
            return method.getMethodName();
        }
        String name = result.getName();
        return name == null ? "" : name;
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        // no-op: only failures are candidates for quarantine
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        // no-op
    }
}
