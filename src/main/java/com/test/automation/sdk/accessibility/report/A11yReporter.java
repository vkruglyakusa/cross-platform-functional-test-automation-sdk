package com.test.automation.sdk.accessibility.report;

/**
 * Pluggable reporting sink for accessibility findings.
 *
 * <p>The library calls these three methods to surface scan output. By keeping the
 * contract this small and string-based, the library stays independent of any
 * specific reporting tool. Messages may contain simple HTML so that rich reporters
 * (ExtentReports, Allure, custom HTML) can render formatting; plain reporters can
 * strip or ignore it.</p>
 *
 * <p>Bridge to your own reporting tool in a few lines — e.g. an ExtentReports adapter:</p>
 * <pre>{@code
 * public class ExtentA11yReporter implements A11yReporter {
 *     public void info(String m) { ExtentTestManager.getTest().info(m); }
 *     public void warn(String m) { ExtentTestManager.getTest().warning(m); }
 *     public void fail(String m) { ExtentTestManager.getTest().fail(m); }
 * }
 * // then: AccessibilityChecker.setReporter(new ExtentA11yReporter());
 * }</pre>
 */
public interface A11yReporter {

    /** Informational message (scan scope, artifact links, pass notices). */
    void info(String message);

    /** Warning message (incomplete rules, moderate/minor violations). */
    void warn(String message);

    /** Failure message (critical/serious violations, scan errors). */
    void fail(String message);
}

