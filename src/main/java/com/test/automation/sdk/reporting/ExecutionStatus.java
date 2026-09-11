package com.test.automation.sdk.reporting;

/**
 * Normalized status values used across logger, Allure, Extent, and future
 * reporting adapters.
 */
public enum ExecutionStatus {
    STARTED,
    PASSED,
    FAILED,
    SKIPPED,
    INFO,
    WARNING
}
