package com.test.automation.sdk.accessibility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for the 5-item migration ported from the standalone
 * {@code AccessibilityTestAutomation} repo: suppression registry wiring, the
 * "Floor Check, Not a Certification" banner, and the library version stamp,
 * as rendered by both {@link AccessibilitySummaryReportGenerator} and
 * {@link AccessibilityExcelReporter}.
 */
class AccessibilitySuppressionReportingTest {

    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        outputDir = Files.createTempDirectory("a11y-suppression-test");
        System.setProperty("reporting.accessibilityDir", outputDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("reporting.accessibilityDir");
        System.clearProperty("accessibility.suppression.rules");
        System.clearProperty("accessibility.suppression.color-contrast.reason");
        System.clearProperty("accessibility.suppression.color-contrast.verified");
        System.clearProperty("accessibility.suppression.color-contrast.expires");
        A11ySuppressionRegistry.load();
    }

    @Test
    void suppressedRuleIsExcludedFromCountsButStillVisibleInReports() throws IOException {
        // A verified false-positive suppression that has NOT expired.
        System.setProperty("accessibility.suppression.rules", "color-contrast");
        System.setProperty("accessibility.suppression.color-contrast.reason",
                "Decorative swatch flagged by axe; verified by design system team as non-content");
        System.setProperty("accessibility.suppression.color-contrast.verified",
                LocalDate.now().minusDays(10).format(DateTimeFormatter.ISO_LOCAL_DATE));
        System.setProperty("accessibility.suppression.color-contrast.expires",
                LocalDate.now().plusMonths(3).format(DateTimeFormatter.ISO_LOCAL_DATE));
        A11ySuppressionRegistry.load();

        writeArtifact("page1_a11y.json", "color-contrast");

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        assertTrue(htmlReport != null && Files.exists(htmlReport), "HTML summary report should be generated");
        String html = Files.readString(htmlReport, StandardCharsets.UTF_8);

        // Suppressed violation is still visible in its own section...
        assertTrue(html.contains("Verified False Positives"), "Should render the suppression section");
        assertTrue(html.contains("color-contrast"), "Suppressed rule id should still be visible");
        // ...but the scan's outcome/violation count reflect zero counted (non-suppressed) violations.
        assertTrue(html.contains(">0<") || html.contains("PASS"),
                "Suppressed-only scan should not count toward FAIL/violations");

        // Floor-check methodology banner is always present.
        assertTrue(html.contains("Floor Check, Not a Certification"), "Should render the floor-check banner");

        // Library version stamp is present and resolved (not the raw placeholder).
        assertTrue(html.contains("SDK v"), "Should render a library version stamp");
        assertFalse(html.contains("${project.version}"), "Version placeholder must be resolved, not raw");

        Path excelReport = AccessibilityExcelReporter.generate();
        assertTrue(excelReport != null && Files.exists(excelReport), "Excel report should be generated");
    }

    private void writeArtifact(String fileName, String ruleId) throws IOException {
        String json = "{"
                + "\"timestamp\":\"2026-01-01 10:00:00\","
                + "\"pageName\":\"Test Page\","
                + "\"outcome\":\"FAIL\","
                + "\"violationCount\":1,"
                + "\"violations\":[{"
                + "\"id\":\"" + ruleId + "\","
                + "\"impact\":\"serious\","
                + "\"description\":\"Elements must meet minimum color contrast ratio\","
                + "\"help\":\"Ensure sufficient color contrast\","
                + "\"helpUrl\":\"https://dequeuniversity.com/rules/axe/color-contrast\","
                + "\"affectedElements\":[\"<span class='swatch'></span>\"]"
                + "}]"
                + "}";
        Files.writeString(outputDir.resolve(fileName), json, StandardCharsets.UTF_8);
    }
}
