package com.test.automation.sdk.accessibility;

import com.test.automation.sdk.accessibility.report.A11yReporter;
import com.test.automation.sdk.accessibility.report.Slf4jReporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the "Final Accessibility Report Publication Boundary Verification"
 * correction: the legacy, continuously-rewritten {@code accessibility-report.xlsx} (produced by
 * {@link AccessibilityChecker#writeExcelReport()}) must remain a working/internal execution
 * artifact and must never be advertised as a user-facing report through any live
 * {@link A11yReporter} sink (Allure/ExtentReports/custom).
 *
 * <p>Prior to this fix, {@code writeExcelReport()} called
 * {@code reporter.info("Excel Report: <a href='accessibility/accessibility-report.xlsx'>...")}
 * after every scan. Since {@code AllureA11yReporter}/{@code ExtentA11yReporter} are both wired in
 * by default ({@code accessibility.reporting.allure}/{@code .extent} default {@code true}), this
 * embedded a clickable link to the legacy, incomplete (no axe {@code incomplete}/Needs Review
 * findings) workbook directly into the live ExtentReports test node and Allure step output on
 * every single scan — while the complete, authoritative
 * {@code accessibility-report_<timestamp>.xlsx}/{@code accessibility-summary.html}
 * reports were never mentioned in those same rich sinks at all (only via console/log at suite
 * end). This test verifies that no reporter sink ever receives a message referencing the legacy
 * workbook.</p>
 */
class AccessibilityReportPublicationBoundaryTest {

    private Path outputDir;
    private final List<String> capturedMessages = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        outputDir = Files.createTempDirectory("a11y-publication-boundary-test");
        System.setProperty("reporting.accessibilityDir", outputDir.toString());
        System.setProperty("reporting.accessibilityWorkingDir", outputDir.toString());
        // This test verifies the legacy workbook is never advertised through live
        // reporter sinks when it IS generated — opt in explicitly (default false).
        System.setProperty("accessibility.reporting.legacyExcel", "true");
        clearInMemoryExcelRows();

        capturedMessages.clear();
        AccessibilityChecker.setReporter(new A11yReporter() {
            @Override
            public void info(String message) { capturedMessages.add(message); }

            @Override
            public void warn(String message) { capturedMessages.add(message); }

            @Override
            public void fail(String message) { capturedMessages.add(message); }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        AccessibilityChecker.setReporter(new Slf4jReporter());
        System.clearProperty("reporting.accessibilityDir");
        System.clearProperty("reporting.accessibilityWorkingDir");
        System.clearProperty("accessibility.reporting.legacyExcel");
        clearInMemoryExcelRows();
    }

    @Test
    void legacyWorkbookIsNeverAdvertisedThroughLiveReporterSinks() throws Exception {
        AccessibilityChecker.InteractionIssue needsReviewIssue = new AccessibilityChecker.InteractionIssue(
                "zoom-reflow", "MODERATE", "WCAG 1.4.10",
                "Content should reflow without loss of information at high zoom levels",
                "<main>", "https://example.com/help/zoom-reflow", true);
        AccessibilityChecker.InteractionIssue confirmedIssue = new AccessibilityChecker.InteractionIssue(
                "keyboard-focus-trap", "CRITICAL", "WCAG 2.1.2",
                "Keyboard focus is trapped and cannot be moved away from this element",
                "<div role=\"dialog\">", "https://example.com/help/keyboard-focus-trap", false);

        invokeWriteInteractionArtifact("zoom-reflow", "Home Page", Collections.singletonList(needsReviewIssue));
        invokeWriteInteractionArtifact("keyboard-focus-trap", "Home Page", Collections.singletonList(confirmedIssue));

        // The legacy live-updating workbook must still be generated (it is an internal
        // artifact, not something to stop producing) ...
        Path legacyExcel = outputDir.resolve("accessibility-report.xlsx");
        assertTrue(Files.exists(legacyExcel), "Legacy accessibility-report.xlsx should still be generated (internal artifact)");

        // ... but no reporter sink should ever be told about it, under any label.
        for (String message : capturedMessages) {
            assertFalse(message != null && message.contains("accessibility-report.xlsx"),
                    "No reporter message should reference the legacy accessibility-report.xlsx: " + message);
            assertFalse(message != null && message.contains("Excel Report"),
                    "No reporter message should advertise an 'Excel Report' link from the legacy path: " + message);
        }
    }

    private void invokeWriteInteractionArtifact(String checkId, String pageName,
                                                 List<AccessibilityChecker.InteractionIssue> issues) throws Exception {
        Method m = AccessibilityChecker.class.getDeclaredMethod("writeInteractionArtifact",
                String.class, String.class, List.class);
        m.setAccessible(true);
        m.invoke(null, checkId, pageName, issues);
    }

    @SuppressWarnings("unchecked")
    private void clearInMemoryExcelRows() throws Exception {
        for (String fieldName : new String[]{"EXCEL_SUMMARY_ROWS", "EXCEL_ISSUE_ROWS"}) {
            java.lang.reflect.Field f = AccessibilityChecker.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            ((List<Object>) f.get(null)).clear();
        }
        java.lang.reflect.Field findings = AccessibilityChecker.class.getDeclaredField("FINDINGS");
        findings.setAccessible(true);
        ((List<Object>) findings.get(null)).clear();
    }
}
