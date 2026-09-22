package com.test.automation.sdk.accessibility.report;

import com.aventstack.extentreports.ExtentTest;
import com.test.automation.sdk.utility.reports.ExtentTestManager;

/**
 * {@link A11yReporter} adapter that routes accessibility findings into the
 * currently active ExtentReports test node (see {@link ExtentTestManager}).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AccessibilityChecker.setReporter(
 *     new CompositeReporter(new Slf4jReporter(), new ExtentA11yReporter()));
 * }</pre>
 *
 * <p>Wired in automatically by {@link A11yReporterFactory} whenever
 * {@code accessibility.reporting.extent} is {@code true} (the default).</p>
 *
 * <p>Silently no-ops when no {@link ExtentTest} is active for the current thread
 * (e.g. {@code ExtentTestManager.startTest(...)} was never called), so this
 * reporter is always safe to install even outside an Extent-managed test
 * lifecycle.</p>
 */
public class ExtentA11yReporter implements A11yReporter {

    private static final String PREFIX = "[A11Y] ";

    @Override
    public void info(String message) {
        ExtentTest test = ExtentTestManager.getTest();
        if (test != null) {
            test.info(PREFIX + message);
        }
    }

    @Override
    public void warn(String message) {
        ExtentTest test = ExtentTestManager.getTest();
        if (test != null) {
            test.warning(PREFIX + message);
        }
    }

    @Override
    public void fail(String message) {
        ExtentTest test = ExtentTestManager.getTest();
        if (test != null) {
            test.fail(PREFIX + message);
        }
    }
}
