package com.test.automation.sdk.reporting;

import java.util.ArrayDeque;
import java.util.Deque;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.test.automation.sdk.utility.reports.ExtentTestManager;

/**
 * Maps neutral execution events into ExtentReports tests, nodes, and evidence.
 */
public final class ExtentExecutionReporter implements ExecutionReporter {

    private final ThreadLocal<Deque<ExtentTest>> activeNodes =
            ThreadLocal.withInitial(ArrayDeque<ExtentTest>::new);

    @Override
    public void report(ExecutionEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case TEST_STARTED:
                if (ExtentTestManager.getTest() == null) {
                    ExtentTestManager.startTest(displayTestName(event));
                }
                break;
            case TEST_PASSED:
                logToRoot(Status.PASS, "Test passed");
                clearNodes();
                break;
            case TEST_FAILED:
                if (event.getThrowable() != null) {
                    root().fail(event.getThrowable());
                } else {
                    logToRoot(Status.FAIL, "Test failed");
                }
                clearNodes();
                break;
            case TEST_SKIPPED:
                logToRoot(Status.SKIP, event.getMessage().isEmpty() ? "Test skipped" : event.getMessage());
                clearNodes();
                break;
            case STEP_STARTED:
                ExtentTest parent = currentNodeOrRoot();
                activeNodes.get().push(parent.createNode(stepTitle(event)));
                break;
            case STEP_PASSED:
                currentNodeOrRoot().pass("Passed in " + safeDuration(event) + " ms");
                popNode();
                break;
            case STEP_FAILED:
                if (event.getThrowable() != null) {
                    currentNodeOrRoot().fail(event.getThrowable());
                } else {
                    currentNodeOrRoot().fail(event.getMessage());
                }
                popNode();
                break;
            case VALIDATION:
                currentNodeOrRoot().info("Validation - " + event.getMessage()
                        + " | expected=" + SecretRedactor.redactMessage(event.getExpectedResult())
                        + " | actual=" + SecretRedactor.redactMessage(event.getActualResult()));
                break;
            case WARNING:
                currentNodeOrRoot().warning(SecretRedactor.redactMessage(event.getMessage()));
                break;
            case INFO:
                currentNodeOrRoot().info(SecretRedactor.redactMessage(event.getMessage()));
                break;
            case SCREENSHOT_CAPTURED:
                addEvidence(event);
                break;
            case DOM_CAPTURED:
            case PAGE_SOURCE_CAPTURED:
                addEvidenceLink(event);
                break;
            case EXCEPTION:
                if (event.getThrowable() != null) {
                    currentNodeOrRoot().fail(event.getThrowable());
                }
                break;
            default:
                break;
        }
    }

    private void addEvidence(ExecutionEvent event) {
        for (ExecutionEvidence evidence : event.getEvidence()) {
            if (evidence == null || evidence.getPath() == null) {
                continue;
            }
            try {
                currentNodeOrRoot().addScreenCaptureFromPath(evidence.getPath().toString(), evidence.getName());
            } catch (Exception ignored) {
            }
        }
    }

    private void addEvidenceLink(ExecutionEvent event) {
        for (ExecutionEvidence evidence : event.getEvidence()) {
            if (evidence == null || evidence.getPath() == null) {
                continue;
            }
            currentNodeOrRoot().info(evidence.getName() + ": " + evidence.getPath());
        }
    }

    private ExtentTest root() {
        ExtentTest test = ExtentTestManager.getTest();
        if (test == null) {
            test = ExtentTestManager.startTest("Unnamed Test");
        }
        return test;
    }

    private ExtentTest currentNodeOrRoot() {
        Deque<ExtentTest> stack = activeNodes.get();
        return stack.isEmpty() ? root() : stack.peek();
    }

    private void popNode() {
        Deque<ExtentTest> stack = activeNodes.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    private void clearNodes() {
        activeNodes.remove();
    }

    private void logToRoot(Status status, String message) {
        root().log(status, SecretRedactor.redactMessage(message));
    }

    private static String displayTestName(ExecutionEvent event) {
        if (event.getTestCaseName() != null && !event.getTestCaseName().isEmpty()) {
            return SecretRedactor.redactMessage(event.getTestCaseName());
        }
        return SecretRedactor.redactMessage(event.getTestName());
    }

    private static String stepTitle(ExecutionEvent event) {
        if (event.getStepNumber() == null) {
            return SecretRedactor.redactMessage(event.getStepName());
        }
        return "Step " + event.getStepNumber() + ": " + SecretRedactor.redactMessage(event.getStepName());
    }

    private static long safeDuration(ExecutionEvent event) {
        return event.getDurationMillis() == null ? 0L : event.getDurationMillis();
    }
}
