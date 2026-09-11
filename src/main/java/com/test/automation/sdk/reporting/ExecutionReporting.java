package com.test.automation.sdk.reporting;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import org.openqa.selenium.WebDriver;
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
            new ExtentExecutionReporter());

    private static final ThreadLocal<ExecutionState> state =
            ThreadLocal.withInitial(ExecutionState::new);

    private ExecutionReporting() {}

    public static void onTestStarted(ITestResult result) {
        ExecutionState current = new ExecutionState();
        current.executionId = UUID.randomUUID().toString();
        current.testName = resolveTestName(result);
        current.testCaseName = safeTestCaseName();
        current.startedAtMillis = System.currentTimeMillis();
        current.status = ExecutionStatus.STARTED;
        captureRuntimeDetails(current, result != null ? result.getInstance() : null);
        state.set(current);
        emit(baseEvent(current, ExecutionEventType.TEST_STARTED, ExecutionStatus.STARTED)
                .message("Test started")
                .build());
    }

    public static void onTestPassed(ITestResult result) {
        ExecutionState current = ensureState();
        current.testCaseName = safeTestCaseName();
        captureRuntimeDetails(current, result != null ? result.getInstance() : null);
        emit(baseEvent(current, ExecutionEventType.TEST_PASSED, ExecutionStatus.PASSED)
                .durationMillis(System.currentTimeMillis() - current.startedAtMillis)
                .message("Test passed")
                .build());
        clear();
    }

    public static void onTestSkipped(ITestResult result) {
        ExecutionState current = ensureState();
        current.testCaseName = safeTestCaseName();
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
        current.testCaseName = safeTestCaseName();
        captureRuntimeDetails(current, result != null ? result.getInstance() : null);
        emit(baseEvent(current, ExecutionEventType.EXCEPTION, ExecutionStatus.FAILED)
                .message(throwable != null ? throwable.getMessage() : "Test failed")
                .throwable(throwable)
                .build());
        publishEvidence(evidence);
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

    public static void publishEvidence(List<ExecutionEvidence> evidence) {
        if (evidence == null) {
            return;
        }
        for (ExecutionEvidence item : evidence) {
            if (item == null || item.getPath() == null) {
                continue;
            }
            ExecutionEventType type = "screenshot".equalsIgnoreCase(item.getType())
                    ? ExecutionEventType.SCREENSHOT_CAPTURED
                    : ("pageSource".equalsIgnoreCase(item.getType())
                        ? ExecutionEventType.PAGE_SOURCE_CAPTURED
                        : ExecutionEventType.DOM_CAPTURED);
            emit(baseEvent(ensureState(), type, ExecutionStatus.INFO)
                    .message(item.getName())
                    .addEvidence(item)
                    .build());
        }
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
        refreshTestCaseName(current);
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
            current.testCaseName = safeTestCaseName();
            current.startedAtMillis = System.currentTimeMillis();
        }
        refreshTestCaseName(current);
        return current;
    }

    private static void refreshTestCaseName(ExecutionState current) {
        String testCaseName = safeTestCaseName();
        if (testCaseName != null && !testCaseName.isEmpty()) {
            current.testCaseName = testCaseName;
        }
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

    private static String resolveTestName(ITestResult result) {
        if (result == null) {
            return Thread.currentThread().getName();
        }
        return result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
    }

    private static String safeTestCaseName() {
        String testCaseName = TestBase.getCurrentTestCaseName();
        return testCaseName == null ? "" : testCaseName;
    }

    static final class ExecutionState {
        private String executionId = "";
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
