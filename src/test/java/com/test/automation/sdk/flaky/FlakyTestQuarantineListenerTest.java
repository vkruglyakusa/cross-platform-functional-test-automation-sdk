package com.test.automation.sdk.flaky;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testng.IClass;
import org.testng.IResultMap;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import com.test.automation.sdk.testbase.TestBase;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FlakyTestQuarantineListener")
class FlakyTestQuarantineListenerTest {

    /** Stub class standing in for a real TestNG test class named "CheckoutTest" -- only its simple name matters. */
    static class CheckoutTest {
    }

    /** Stub class standing in for a real TestNG test class named "NewTest" -- only its simple name matters. */
    static class NewTest {
    }

    @TempDir
    File tempDir;

    @BeforeEach
    void resetTestCaseName() {
        TestBase.setCurrentTestCaseName(null);
    }

    @AfterEach
    void clearOverrides() {
        System.clearProperty("flaky.quarantine.enabled");
        System.clearProperty("flaky.minRunsForQuarantine");
        System.clearProperty("flaky.maxFailureRatePercent");
        System.clearProperty("reporting.analytics.directory");
        TestBase.setCurrentTestCaseName(null);
    }

    private void writeHistory(String... lines) throws Exception {
        Path file = tempDir.toPath().resolve("run1.jsonl");
        Files.write(file, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
    }

    private ITestResult mockResult(Class<?> realClass, String methodName) {
        ITestResult result = mock(ITestResult.class);
        IClass testClass = mock(IClass.class);
        doReturn(realClass).when(testClass).getRealClass();
        when(result.getTestClass()).thenReturn(testClass);
        ITestNGMethod method = mock(ITestNGMethod.class);
        when(method.getMethodName()).thenReturn(methodName);
        when(result.getMethod()).thenReturn(method);
        when(result.getInstance()).thenReturn(null);
        return result;
    }

    @Test
    @DisplayName("feature disabled by default: a known-flaky failure is left as FAILED")
    void disabledByDefaultLeavesFailureUnchanged() throws Exception {
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        writeHistory(
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}");

        FlakyTestQuarantineListener listener = new FlakyTestQuarantineListener();
        ITestResult result = mockResult(CheckoutTest.class, "testCheckout");

        listener.onTestFailure(result);

        verify(result, never()).setStatus(ITestResult.SKIP);
    }

    @Test
    @DisplayName("enabled + known flaky: the result is reclassified from FAILED to SKIP")
    void enabledAndKnownFlakyReclassifiesToSkip() throws Exception {
        System.setProperty("flaky.quarantine.enabled", "true");
        System.setProperty("flaky.minRunsForQuarantine", "5");
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        writeHistory(
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}");

        FlakyTestQuarantineListener listener = new FlakyTestQuarantineListener();
        ITestContext context = mock(ITestContext.class);
        IResultMap failedTests = mock(IResultMap.class);
        IResultMap skippedTests = mock(IResultMap.class);
        when(context.getFailedTests()).thenReturn(failedTests);
        when(context.getSkippedTests()).thenReturn(skippedTests);
        ITestResult result = mockResult(CheckoutTest.class, "testCheckout");
        when(result.getTestContext()).thenReturn(context);

        listener.onTestFailure(result);

        verify(result, times(1)).setStatus(ITestResult.SKIP);
        verify(failedTests, times(1)).removeResult(result);
        verify(skippedTests, times(1)).addResult(result);
    }

    @Test
    @DisplayName("enabled but not-yet-flaky (first failure, no history): the result is left as FAILED")
    void enabledButFirstFailureLeavesResultUnchanged() throws Exception {
        System.setProperty("flaky.quarantine.enabled", "true");
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        // no history file written -- this is a first-time failure

        FlakyTestQuarantineListener listener = new FlakyTestQuarantineListener();
        ITestResult result = mockResult(NewTest.class, "testNew");

        listener.onTestFailure(result);

        verify(result, never()).setStatus(ITestResult.SKIP);
    }
}
