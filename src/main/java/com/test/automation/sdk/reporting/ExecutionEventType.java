package com.test.automation.sdk.reporting;

/**
 * Small, technology-neutral set of execution event types emitted by the SDK's
 * reporting layer.
 */
public enum ExecutionEventType {
    TEST_STARTED,
    TEST_PASSED,
    TEST_FAILED,
    TEST_SKIPPED,
    STEP_STARTED,
    STEP_PASSED,
    STEP_FAILED,
    ACTION_STARTED,
    ACTION_COMPLETED,
    ACTION_FAILED,
    VALIDATION,
    WARNING,
    INFO,
    SCREENSHOT_CAPTURED,
    DOM_CAPTURED,
    PAGE_SOURCE_CAPTURED,
    EXCEPTION
}
