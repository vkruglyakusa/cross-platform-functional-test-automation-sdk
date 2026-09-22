package com.test.automation.sdk.accessibility.report;

import com.test.automation.sdk.accessibility.config.A11yConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the composite {@link A11yReporter} used by the automatic TestNG listener,
 * the JUnit 5 extension, and {@code TestBase.initAccessibility()} — driven entirely
 * by configuration, so consumers control which sinks receive live accessibility
 * findings without writing any wiring code themselves.
 *
 * <h3>Configuration ({@code accessibility.reporting.*} — all default {@code true})</h3>
 * <pre>
 * accessibility:
 *   reporting:
 *     allure: true   # -Daccessibility.reporting.allure=false to disable
 *     extent: true   # -Daccessibility.reporting.extent=false to disable
 *     excel:  true   # consumed separately -- see A11yTestNGListener / A11yExtension,
 *                    # which gate their AccessibilityExcelReporter.generate() call on this flag
 * </pre>
 *
 * <p>SLF4J logging is always included as the baseline sink regardless of
 * configuration, so accessibility findings are never silently lost even when
 * every rich reporter is disabled.</p>
 */
public final class A11yReporterFactory {

    private A11yReporterFactory() {
    }

    /** Returns {@code true} when live Allure step reporting is enabled (default {@code true}). */
    public static boolean isAllureEnabled() {
        return A11yConfig.getBoolean("accessibility.reporting.allure", true);
    }

    /** Returns {@code true} when live ExtentReports step reporting is enabled (default {@code true}). */
    public static boolean isExtentEnabled() {
        return A11yConfig.getBoolean("accessibility.reporting.extent", true);
    }

    /**
     * Returns {@code true} when the {@code accessibility-report.xlsx} artifact should be
     * (re)generated at suite end (default {@code true}) — part of the default reporting
     * configuration whenever accessibility scanning is enabled.
     */
    public static boolean isExcelEnabled() {
        return A11yConfig.getBoolean("accessibility.reporting.excel", true);
    }

    /** Builds the composite reporter honoring the current {@code accessibility.reporting.*} configuration. */
    public static A11yReporter buildDefault() {
        List<A11yReporter> sinks = new ArrayList<>();
        sinks.add(new Slf4jReporter());
        if (isAllureEnabled()) {
            sinks.add(new AllureA11yReporter());
        }
        if (isExtentEnabled()) {
            sinks.add(new ExtentA11yReporter());
        }
        return new CompositeReporter(sinks.toArray(new A11yReporter[0]));
    }
}
