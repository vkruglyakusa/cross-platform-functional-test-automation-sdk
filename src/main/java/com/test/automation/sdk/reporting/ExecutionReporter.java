package com.test.automation.sdk.reporting;

/**
 * Adapter interface for execution event sinks such as logs, Allure,
 * ExtentReports, and future RCA/reporting services.
 */
public interface ExecutionReporter {

    void report(ExecutionEvent event);
}
