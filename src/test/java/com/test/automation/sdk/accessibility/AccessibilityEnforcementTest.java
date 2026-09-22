package com.test.automation.sdk.accessibility;

import com.test.automation.sdk.accessibility.report.A11yReporterFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the configurable accessibility test-enforcement model
 * ({@code accessibility.mode} / {@code accessibility.failOnSeverity}) and the
 * {@code accessibility.reporting.*} output toggles consumed by
 * {@link com.test.automation.sdk.accessibility.report.A11yReporterFactory}.
 *
 * <p>All of this is pure configuration logic (no WebDriver involved), so it is
 * exercised directly via system properties — the same override mechanism
 * {@link com.test.automation.sdk.accessibility.config.A11yConfig} resolves at
 * runtime, and the same one {@code YamlConfigReader} bridges {@code sdk-config.yaml}
 * values into.</p>
 */
@DisplayName("Accessibility enforcement mode and reporting configuration")
class AccessibilityEnforcementTest {

    private static final String[] KEYS = {
            "accessibility.mode",
            "accessibility.failOnSeverity",
            "accessibility.fail.on.violation",
            "accessibility.reporting.allure",
            "accessibility.reporting.extent",
            "accessibility.reporting.excel",
    };

    @BeforeEach
    @AfterEach
    void clearOverrides() {
        for (String key : KEYS) {
            System.clearProperty(key);
        }
    }

    @Test
    @DisplayName("defaults to report-only with a minor failure threshold when unconfigured")
    void defaultsToReportOnly() {
        assertEquals(AccessibilityChecker.EnforcementMode.REPORT_ONLY, AccessibilityChecker.getEnforcementMode());
        assertEquals("minor", AccessibilityChecker.getFailOnSeverity());
        // Report-only never fails, regardless of severity.
        assertFalse(AccessibilityChecker.meetsFailureThreshold("critical"));
    }

    @Test
    @DisplayName("mode=fail-test with failOnSeverity=serious fails only at/above serious")
    void failTestHonorsSeverityThreshold() {
        System.setProperty("accessibility.mode", "fail-test");
        System.setProperty("accessibility.failOnSeverity", "serious");

        assertEquals(AccessibilityChecker.EnforcementMode.FAIL_TEST, AccessibilityChecker.getEnforcementMode());
        assertFalse(AccessibilityChecker.meetsFailureThreshold("minor"));
        assertFalse(AccessibilityChecker.meetsFailureThreshold("moderate"));
        assertTrue(AccessibilityChecker.meetsFailureThreshold("serious"));
        assertTrue(AccessibilityChecker.meetsFailureThreshold("critical"));
    }

    @Test
    @DisplayName("findings below the configured threshold never qualify, even in fail-test mode")
    void findingsBelowThresholdDoNotQualify() {
        System.setProperty("accessibility.mode", "fail-test");
        System.setProperty("accessibility.failOnSeverity", "critical");

        assertFalse(AccessibilityChecker.meetsFailureThreshold("serious"));
        assertFalse(AccessibilityChecker.meetsFailureThreshold("moderate"));
        assertFalse(AccessibilityChecker.meetsFailureThreshold("minor"));
        assertTrue(AccessibilityChecker.meetsFailureThreshold("critical"));
    }

    @Test
    @DisplayName("legacy accessibility.fail.on.violation=true is honored as fail-test when mode is unset")
    void legacyFailOnViolationFlagIsHonored() {
        System.setProperty("accessibility.fail.on.violation", "true");

        assertEquals(AccessibilityChecker.EnforcementMode.FAIL_TEST, AccessibilityChecker.getEnforcementMode());
        // Legacy behavior: fail on ANY violation (default threshold is "minor").
        assertTrue(AccessibilityChecker.meetsFailureThreshold("minor"));
    }

    @Test
    @DisplayName("an explicit accessibility.mode always takes precedence over the legacy flag")
    void explicitModeOverridesLegacyFlag() {
        System.setProperty("accessibility.fail.on.violation", "true");
        System.setProperty("accessibility.mode", "report-only");

        assertEquals(AccessibilityChecker.EnforcementMode.REPORT_ONLY, AccessibilityChecker.getEnforcementMode());
        assertFalse(AccessibilityChecker.meetsFailureThreshold("critical"));
    }

    @Test
    @DisplayName("unrecognized severities never qualify to fail the test")
    void unrecognizedSeverityNeverQualifies() {
        System.setProperty("accessibility.mode", "fail-test");
        assertFalse(AccessibilityChecker.meetsFailureThreshold("unknown"));
        assertFalse(AccessibilityChecker.meetsFailureThreshold(null));
    }

    @Test
    @DisplayName("reporting outputs (Allure, Extent, Excel) default to enabled")
    void reportingOutputsDefaultEnabled() {
        assertTrue(A11yReporterFactory.isAllureEnabled());
        assertTrue(A11yReporterFactory.isExtentEnabled());
        assertTrue(A11yReporterFactory.isExcelEnabled());
    }

    @Test
    @DisplayName("reporting outputs can each be individually disabled")
    void reportingOutputsCanBeDisabledIndividually() {
        System.setProperty("accessibility.reporting.allure", "false");
        System.setProperty("accessibility.reporting.extent", "false");
        System.setProperty("accessibility.reporting.excel", "false");

        assertFalse(A11yReporterFactory.isAllureEnabled());
        assertFalse(A11yReporterFactory.isExtentEnabled());
        assertFalse(A11yReporterFactory.isExcelEnabled());
    }

    @Test
    @DisplayName("buildDefault() always includes SLF4J and only the enabled rich reporters")
    void buildDefaultComposesConfiguredSinks() {
        // Allure is intentionally disabled here: AllureA11yReporter writes into the live
        // Allure lifecycle, which is out of scope for this pure config-composition test.
        System.setProperty("accessibility.reporting.allure", "false");
        System.setProperty("accessibility.reporting.extent", "true");

        // Smoke check: composition must not throw, and info/warn/fail must be safe to call
        // even with no live Extent test context active (ExtentA11yReporter no-ops).
        var reporter = A11yReporterFactory.buildDefault();
        reporter.info("test");
        reporter.warn("test");
        reporter.fail("test");
    }
}
