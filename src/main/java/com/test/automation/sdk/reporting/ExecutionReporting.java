package com.test.automation.sdk.reporting;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import org.openqa.selenium.WebDriver;
import org.testng.IClass;
import org.testng.ISuite;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;

import com.test.automation.sdk.mobile.testbase.MobileTestBase;
import com.test.automation.sdk.testbase.TestBase;

/**
 * Central execution-reporting facade. The SDK emits one neutral event stream
 * here and adapters translate it to logs, Allure, Extent, and evidence.
 */
public final class ExecutionReporting {

    private static volatile ExecutionReporter reporter = new CompositeExecutionReporter(
            new ExecutionLogReporter(),
            new AllureExecutionReporter(),
            new ExtentExecutionReporter(),
            new AnalyticsExecutionReporter());

    private static final ThreadLocal<ExecutionState> state =
            ThreadLocal.withInitial(ExecutionState::new);

    /**
     * OBS-Allure-fix: independent, longer-lived snapshot of the current test's
     * suite/testNgTestName/className, read by {@link AllureLabelLifecycleListener}.
     * {@code state} is eagerly cleared by {@link #clear()} as soon as
     * onTestPassed/onTestFailed/onTestSkipped fires (this SDK's own
     * {@code Listener} callback), but Allure's own {@code AllureTestNg} listener
     * -- a separate, ServiceLoader-registered {@code ITestListener} -- is not
     * guaranteed to run its {@code writeTestCase(...)} (and therefore
     * {@code beforeTestWrite}) before or after that same-named TestNG callback on
     * this SDK's listener. If {@code state} were used directly and clear() ran
     * first, the label listener would see empty metadata. This ThreadLocal is
     * refreshed at the same points as {@code state}'s suite/test/class fields
     * but is only removed by {@link #clearAllureLabelMetadata()}, which
     * {@link AllureLabelLifecycleListener#afterTestWrite} calls once Allure has
     * actually finished writing the test case -- guaranteeing metadata survives
     * regardless of listener ordering.
     */
    private static final ThreadLocal<CurrentTestMetadata> allureLabelMetadata =
            ThreadLocal.withInitial(() -> new CurrentTestMetadata("", "", ""));

    private static final String UNKNOWN_SUITE = "Unknown Suite";

    private ExecutionReporting() {}

    public static void onSuiteStarted(ISuite suite) {
        emit(ExecutionEvent.builder(ExecutionEventType.SUITE_STARTED)
                .status(ExecutionStatus.STARTED)
                .suiteName(resolveSuiteName(suite))
                .message("Suite started")
                .build());
    }

    public static void onSuiteFinished(ISuite suite) {
        emit(ExecutionEvent.builder(ExecutionEventType.SUITE_COMPLETED)
                .status(ExecutionStatus.PASSED)
                .suiteName(resolveSuiteName(suite))
                .message("Suite completed")
                .build());
    }

    public static void onTestStarted(ITestResult result) {
        ExecutionState current = new ExecutionState();
        current.executionId = UUID.randomUUID().toString();
        current.startedAtMillis = System.currentTimeMillis();
        current.status = ExecutionStatus.STARTED;
        refreshExecutionMetadata(current, result);
        captureRuntimeDetails(current, result != null ? result.getInstance() : null);
        state.set(current);
        emit(baseEvent(current, ExecutionEventType.TEST_STARTED, ExecutionStatus.STARTED)
                .message("Test started")
                .build());
    }

    public static void onTestPassed(ITestResult result) {
        ExecutionState current = ensureState();
        refreshExecutionMetadata(current, result);
        captureRuntimeDetails(current, result != null ? result.getInstance() : null);
        emit(baseEvent(current, ExecutionEventType.TEST_PASSED, ExecutionStatus.PASSED)
                .durationMillis(System.currentTimeMillis() - current.startedAtMillis)
                .message("Test passed")
                .build());
        clear();
    }

    public static void onTestSkipped(ITestResult result) {
        ExecutionState current = ensureState();
        refreshExecutionMetadata(current, result);
        captureRuntimeDetails(current, result != null ? result.getInstance() : null);
        emit(baseEvent(current, ExecutionEventType.TEST_SKIPPED, ExecutionStatus.SKIPPED)
                .durationMillis(System.currentTimeMillis() - current.startedAtMillis)
                .message(result != null && result.getThrowable() != null
                        ? result.getThrowable().getMessage()
                        : "Test skipped")
                .throwable(result != null ? result.getThrowable() : null)
                .build());
        clear();
    }

    public static void onTestFailed(ITestResult result, Throwable throwable, List<ExecutionEvidence> evidence) {
        ExecutionState current = ensureState();
        refreshExecutionMetadata(current, result);
        captureRuntimeDetails(current, result != null ? result.getInstance() : null);
        emit(baseEvent(current, ExecutionEventType.EXCEPTION, ExecutionStatus.FAILED)
                .message(throwable != null ? throwable.getMessage() : "Test failed")
                .throwable(throwable)
                .build());
        publishEvidence(evidence);
        writeRcaBundle(current, throwable, evidence);
        String detail = current.lastSuccessfulStepName == null || current.lastSuccessfulStepName.isEmpty()
                ? "Test failed"
                : "Test failed after last completed step [" + current.lastSuccessfulStepName + "]";
        emit(baseEvent(current, ExecutionEventType.TEST_FAILED, ExecutionStatus.FAILED)
                .durationMillis(System.currentTimeMillis() - current.startedAtMillis)
                .message(detail)
                .throwable(throwable)
                .evidence(evidence)
                .build());
        clear();
    }

    /**
     * Tier 3 (#9) automated RCA-to-fix loop: consolidates identity, exception
     * chain, evidence paths, and a fresh SDK log tail into one JSON file per
     * failure. Never throws -- see {@link RcaBundleWriter#write}.
     */
    private static void writeRcaBundle(ExecutionState current, Throwable throwable, List<ExecutionEvidence> evidence) {
        try {
            RcaBundleWriter.write(RcaBundleWriter.builder()
                    .testCaseName(current.testCaseName)
                    .className(current.className)
                    .methodName(current.methodName)
                    .suiteName(current.suiteName)
                    .testNgTestName(current.testNgTestName)
                    .platform(current.platform)
                    .browser(current.browser)
                    .device(current.device)
                    .lastCompletedStep(current.lastSuccessfulStepName)
                    .throwable(throwable)
                    .evidence(evidence));
        } catch (Exception e) {
            // Never let bundle assembly affect test-failure reporting itself.
        }
    }

    public static void publishEvidence(List<ExecutionEvidence> evidence) {
        if (evidence == null) {
            return;
        }
        for (ExecutionEvidence item : evidence) {
            if (item == null || item.getPath() == null) {
                continue;
            }
            ExecutionEventType type = resolveEvidenceEventType(item.getType());
            emit(baseEvent(ensureState(), type, ExecutionStatus.INFO)
                    .message(item.getName())
                    .addEvidence(item)
                    .build());
        }
    }

    /**
     * Maps an {@link ExecutionEvidence#getType()} string to the neutral event type
     * used to publish it. Unknown/legacy types fall back to {@code DOM_CAPTURED},
     * matching the pre-v1.5.1 default so existing evidence producers keep working.
     */
    private static ExecutionEventType resolveEvidenceEventType(String evidenceType) {
        if ("screenshot".equalsIgnoreCase(evidenceType)) {
            return ExecutionEventType.SCREENSHOT_CAPTURED;
        }
        if ("pageSource".equalsIgnoreCase(evidenceType)) {
            return ExecutionEventType.PAGE_SOURCE_CAPTURED;
        }
        if ("apiPayload".equalsIgnoreCase(evidenceType)) {
            return ExecutionEventType.API_PAYLOAD_CAPTURED;
        }
        if ("browserConsole".equalsIgnoreCase(evidenceType)) {
            return ExecutionEventType.BROWSER_CONSOLE_CAPTURED;
        }
        if ("networkTrace".equalsIgnoreCase(evidenceType)) {
            return ExecutionEventType.NETWORK_TRACE_CAPTURED;
        }
        return ExecutionEventType.DOM_CAPTURED;
    }

    public static void info(TestBase owner, String message) {
        emit(buildContextEvent(owner, ExecutionEventType.INFO, ExecutionStatus.INFO)
                .message(message)
                .build());
    }

    public static void warning(TestBase owner, String message) {
        emit(buildContextEvent(owner, ExecutionEventType.WARNING, ExecutionStatus.WARNING)
                .message(message)
                .build());
    }

    public static void validation(TestBase owner, String description, String expected, String actual) {
        emit(buildContextEvent(owner, ExecutionEventType.VALIDATION, ExecutionStatus.INFO)
                .message(description)
                .expectedResult(expected)
                .actualResult(actual)
                .build());
    }

    public static void actionStarted(TestBase owner, String action, String locator, String detail) {
        emit(buildContextEvent(owner, ExecutionEventType.ACTION_STARTED, ExecutionStatus.STARTED)
                .action(action)
                .locator(locator)
                .message(detail)
                .build());
    }

    public static void actionCompleted(TestBase owner, String action, String locator, String detail, Long durationMillis) {
        emit(buildContextEvent(owner, ExecutionEventType.ACTION_COMPLETED, ExecutionStatus.PASSED)
                .action(action)
                .locator(locator)
                .message(detail)
                .durationMillis(durationMillis)
                .build());
    }

    public static void actionFailed(TestBase owner, String action, String locator, String detail, Throwable throwable, Long durationMillis) {
        emit(buildContextEvent(owner, ExecutionEventType.ACTION_FAILED, ExecutionStatus.FAILED)
                .action(action)
                .locator(locator)
                .message(detail)
                .throwable(throwable)
                .durationMillis(durationMillis)
                .build());
    }

    /**
     * Owner-less overload of {@link #info(TestBase, String)} for callers
     * (e.g. {@code com.test.automation.sdk.api.ApiTestBase}) that have no
     * {@link TestBase}/WebDriver instance to report against.
     */
    public static void info(String message) {
        info(null, message);
    }

    /** Owner-less overload of {@link #warning(TestBase, String)}. */
    public static void warning(String message) {
        warning(null, message);
    }

    /** Owner-less overload of {@link #validation(TestBase, String, String, String)}. */
    public static void validation(String description, String expected, String actual) {
        validation(null, description, expected, actual);
    }

    public static void actionStarted(String action, String locator, String detail) {
        actionStarted(null, action, locator, detail);
    }

    public static void actionCompleted(String action, String locator, String detail, Long durationMillis) {
        actionCompleted(null, action, locator, detail, durationMillis);
    }

    public static void actionFailed(String action, String locator, String detail, Throwable throwable, Long durationMillis) {
        actionFailed(null, action, locator, detail, throwable, durationMillis);
    }

    public static void publishEvidence(Path path, String name, String type) {
        List<ExecutionEvidence> evidence = new ArrayList<ExecutionEvidence>();
        if ("screenshot".equalsIgnoreCase(type)) {
            evidence.add(ExecutionEvidence.screenshot(name, path));
        } else if ("pageSource".equalsIgnoreCase(type)) {
            evidence.add(ExecutionEvidence.pageSource(name, path));
        } else if ("apiPayload".equalsIgnoreCase(type)) {
            evidence.add(ExecutionEvidence.apiPayload(name, path));
        } else {
            evidence.add(ExecutionEvidence.domDump(name, path));
        }
        publishEvidence(evidence);
    }

    public static void step(TestBase owner, String stepName, TestBase.StepAction action) throws Exception {
        runStep(owner, stepName, action);
    }

    public static <T> T step(TestBase owner, String stepName, TestBase.StepSupplier<T> action) throws Exception {
        return runStep(owner, stepName, action);
    }

    static void setReporterForTests(ExecutionReporter testReporter) {
        reporter = testReporter;
    }

    static void resetReporterForTests() {
        reporter = new CompositeExecutionReporter(
                new ExecutionLogReporter(),
                new AllureExecutionReporter(),
                new ExtentExecutionReporter());
    }

    static void clear() {
        state.remove();
        TestBase.clearCurrentTestCaseName();
    }

    static ExecutionState currentStateForTests() {
        return state.get();
    }

    private static void runStep(TestBase owner, String stepName, TestBase.StepAction action) throws Exception {
        runStep(owner, stepName, new TestBase.StepSupplier<Void>() {
            @Override
            public Void get() throws Exception {
                action.run();
                return null;
            }
        });
    }

    private static <T> T runStep(TestBase owner, String stepName, TestBase.StepSupplier<T> action) throws Exception {
        ExecutionState current = ensureState();
        refreshExecutionMetadata(current, null);
        captureRuntimeDetails(current, owner);
        StepFrame frame = new StepFrame(++current.nextStepNumber, stepName, UUID.randomUUID().toString(), System.currentTimeMillis());
        current.activeSteps.push(frame);
        emit(baseEvent(current, ExecutionEventType.STEP_STARTED, ExecutionStatus.STARTED)
                .stepId(frame.stepId)
                .stepNumber(frame.stepNumber)
                .stepName(frame.stepName)
                .message("Step started")
                .build());
        try {
            T result = action.get();
            long finishedAt = System.currentTimeMillis();
            current.lastSuccessfulStepName = stepName;
            emit(baseEvent(current, ExecutionEventType.STEP_PASSED, ExecutionStatus.PASSED)
                    .stepId(frame.stepId)
                    .stepNumber(frame.stepNumber)
                    .stepName(frame.stepName)
                    .durationMillis(finishedAt - frame.startedAtMillis)
                    .timestampMillis(finishedAt)
                    .message("Step passed")
                    .build());
            return result;
        } catch (Exception e) {
            long finishedAt = System.currentTimeMillis();
            current.lastFailedStepName = stepName;
            emit(baseEvent(current, ExecutionEventType.STEP_FAILED, ExecutionStatus.FAILED)
                    .stepId(frame.stepId)
                    .stepNumber(frame.stepNumber)
                    .stepName(frame.stepName)
                    .durationMillis(finishedAt - frame.startedAtMillis)
                    .timestampMillis(finishedAt)
                    .message(e.getMessage())
                    .throwable(e)
                    .build());
            throw e;
        } finally {
            if (!current.activeSteps.isEmpty()) {
                current.activeSteps.pop();
            }
        }
    }

    private static ExecutionState ensureState() {
        ExecutionState current = state.get();
        if (current.executionId == null || current.executionId.isEmpty()) {
            current.executionId = UUID.randomUUID().toString();
            current.testName = Thread.currentThread().getName();
            current.suiteName = UNKNOWN_SUITE;
            current.testCaseName = safeTestCaseName();
            current.startedAtMillis = System.currentTimeMillis();
        }
        refreshExecutionMetadata(current, null);
        return current;
    }

    private static void refreshExecutionMetadata(ExecutionState current, ITestResult result) {
        if (current == null) {
            return;
        }
        String testCaseName = safeTestCaseName();
        if (testCaseName != null && !testCaseName.isEmpty()) {
            current.testCaseName = testCaseName;
        }
        if (result == null) {
            return;
        }
        current.suiteName = resolveSuiteName(result);
        current.testNgTestName = resolveTestNgTestName(result);
        current.className = resolveClassName(result);
        current.methodName = resolveMethodName(result);
        current.testName = resolveTestName(current.className, current.methodName);
        allureLabelMetadata.set(new CurrentTestMetadata(current.suiteName, current.testNgTestName, current.className));
    }

    private static ExecutionEvent.Builder buildContextEvent(TestBase owner, ExecutionEventType type, ExecutionStatus status) {
        ExecutionState current = ensureState();
        captureRuntimeDetails(current, owner);
        return baseEvent(current, type, status);
    }

    private static ExecutionEvent.Builder baseEvent(ExecutionState current, ExecutionEventType type, ExecutionStatus status) {
        StepFrame activeStep = current.activeSteps.peek();
        ExecutionEvent.Builder builder = ExecutionEvent.builder(type)
                .status(status)
                .executionId(current.executionId)
                .suiteName(current.suiteName)
                .testNgTestName(current.testNgTestName)
                .className(current.className)
                .methodName(current.methodName)
                .testName(current.testName)
                .testCaseName(current.testCaseName)
                .browser(current.browser)
                .platform(current.platform)
                .device(current.device)
                .url(current.url)
                .timestampMillis(System.currentTimeMillis());
        if (activeStep != null) {
            builder.stepId(activeStep.stepId)
                    .stepNumber(activeStep.stepNumber)
                    .stepName(activeStep.stepName);
        }
        return builder;
    }

    private static void captureRuntimeDetails(ExecutionState current, Object owner) {
        if (!(owner instanceof TestBase)) {
            return;
        }
        TestBase testBase = (TestBase) owner;
        if (testBase.browser != null && !testBase.browser.isEmpty()) {
            current.browser = testBase.browser;
        }
        WebDriver driver = testBase.driver;
        if (driver != null) {
            try {
                current.url = driver.getCurrentUrl();
            } catch (Exception ignored) {
            }
        }
        if (owner instanceof MobileTestBase) {
            MobileTestBase mobile = (MobileTestBase) owner;
            try {
                current.platform = mobile.getCurrentPlatformOS();
            } catch (Exception ignored) {
            }
        } else if (current.platform == null || current.platform.isEmpty()) {
            current.platform = "web";
        }
        try {
            Method method = owner.getClass().getMethod("getDeviceName");
            Object value = method.invoke(owner);
            if (value != null) {
                current.device = String.valueOf(value);
            }
        } catch (Exception ignored) {
        }
    }

    private static void emit(ExecutionEvent event) {
        if (event == null) {
            return;
        }
        reporter.report(event);
    }

    private static String resolveTestName(String className, String methodName) {
        if (className == null || className.isEmpty()) {
            return methodName == null ? "" : methodName;
        }
        if (methodName == null || methodName.isEmpty()) {
            return className;
        }
        return className + "." + methodName;
    }

    private static String resolveSuiteName(ITestResult result) {
        String suiteName = "";
        if (result != null) {
            ITestContext context = result.getTestContext();
            if (context != null) {
                ISuite suite = context.getSuite();
                if (suite != null) {
                    suiteName = trimToEmpty(suite.getName());
                }
                if (isMeaningfulSuiteName(suiteName)) {
                    return suiteName;
                }
                String contextName = trimToEmpty(context.getName());
                if (isMeaningfulFallbackSuiteName(contextName)) {
                    return contextName;
                }
            }
        }
        String className = resolveClassName(result);
        return className.isEmpty() ? UNKNOWN_SUITE : className;
    }

    private static String resolveSuiteName(ISuite suite) {
        String suiteName = suite == null ? "" : trimToEmpty(suite.getName());
        return isMeaningfulSuiteName(suiteName) ? suiteName : UNKNOWN_SUITE;
    }

    private static String resolveTestNgTestName(ITestResult result) {
        if (result == null) {
            return "";
        }
        ITestContext context = result.getTestContext();
        return context == null ? "" : trimToEmpty(context.getName());
    }

    private static String resolveClassName(ITestResult result) {
        if (result == null) {
            return "";
        }
        IClass testClass = result.getTestClass();
        if (testClass == null) {
            return "";
        }
        Class<?> realClass = testClass.getRealClass();
        if (realClass != null) {
            return trimToEmpty(realClass.getSimpleName());
        }
        String fallbackName = trimToEmpty(testClass.getName());
        if (fallbackName.isEmpty()) {
            return "";
        }
        int lastDot = fallbackName.lastIndexOf('.');
        return lastDot >= 0 ? fallbackName.substring(lastDot + 1) : fallbackName;
    }

    private static String resolveMethodName(ITestResult result) {
        if (result == null) {
            return "";
        }
        ITestNGMethod method = result.getMethod();
        if (method != null && method.getMethodName() != null) {
            return method.getMethodName();
        }
        String name = result.getName();
        return name == null ? "" : name;
    }

    private static boolean isMeaningfulSuiteName(String suiteName) {
        return !suiteName.isEmpty() && !"Surefire suite".equalsIgnoreCase(suiteName);
    }

    private static boolean isMeaningfulFallbackSuiteName(String candidate) {
        return !candidate.isEmpty() && !"Surefire test".equalsIgnoreCase(candidate);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeTestCaseName() {
        String testCaseName = TestBase.getCurrentTestCaseName();
        return testCaseName == null ? "" : testCaseName;
    }

    /**
     * Read-only snapshot of the current thread's suite/test/class metadata, captured
     * without mutating the underlying execution state. Used by {@link AllureLabelLifecycleListener}
     * to (re)apply neutral labels at the exact moment Allure's own lifecycle processes a test
     * case -- this is independent of TestNG's {@code ITestListener} invocation order, which is
     * not guaranteed relative to service-loaded listeners such as {@code AllureTestNg}.
     */
    static CurrentTestMetadata peekCurrentTestMetadata() {
        return allureLabelMetadata.get();
    }

    /**
     * OBS-Allure-fix: called by {@link AllureLabelLifecycleListener#afterTestWrite}
     * once Allure has finished writing the test case, so the metadata snapshot
     * does not leak into the next test executed on this thread.
     */
    static void clearAllureLabelMetadata() {
        allureLabelMetadata.remove();
    }

    static final class CurrentTestMetadata {
        final String suiteName;
        final String testNgTestName;
        final String className;

        CurrentTestMetadata(String suiteName, String testNgTestName, String className) {
            this.suiteName = suiteName == null ? "" : suiteName;
            this.testNgTestName = testNgTestName == null ? "" : testNgTestName;
            this.className = className == null ? "" : className;
        }
    }

    static final class ExecutionState {
        private String executionId = "";
        private String suiteName = "";
        private String testNgTestName = "";
        private String className = "";
        private String methodName = "";
        private String testName = "";
        private String testCaseName = "";
        private String platform = "";
        private String browser = "";
        private String device = "";
        private String url = "";
        private long startedAtMillis;
        private int nextStepNumber;
        private String lastSuccessfulStepName = "";
        private String lastFailedStepName = "";
        private ExecutionStatus status = ExecutionStatus.INFO;
        private final Deque<StepFrame> activeSteps = new ArrayDeque<StepFrame>();
    }

    private static final class StepFrame {
        private final int stepNumber;
        private final String stepName;
        private final String stepId;
        private final long startedAtMillis;

        private StepFrame(int stepNumber, String stepName, String stepId, long startedAtMillis) {
            this.stepNumber = stepNumber;
            this.stepName = stepName == null ? "" : stepName;
            this.stepId = stepId == null ? "" : stepId;
            this.startedAtMillis = startedAtMillis;
        }
    }
}
