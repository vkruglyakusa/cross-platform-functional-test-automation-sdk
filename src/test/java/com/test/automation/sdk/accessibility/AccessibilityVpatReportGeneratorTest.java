package com.test.automation.sdk.accessibility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for {@link AccessibilityVpatReportGenerator} -- verifies the draft VPAT
 * table correctly distinguishes "Does Not Support" (evaluated, violation found),
 * "Supports (No Automated Findings)" (evaluated, clean), and "Not Evaluated"
 * (never covered by this run's automated checks), and always renders the
 * not-an-official-VPAT disclaimer.
 */
class AccessibilityVpatReportGeneratorTest {

    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        outputDir = Files.createTempDirectory("a11y-vpat-test");
        System.setProperty("reporting.accessibilityDir", outputDir.toString());
        System.setProperty("reporting.accessibilityWorkingDir", outputDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("reporting.accessibilityDir");
        System.clearProperty("reporting.accessibilityWorkingDir");
    }

    @Test
    void rendersEvaluatedAndUnevaluatedCriteriaCorrectly() throws IOException {
        // 1.4.3 (Contrast) -- evaluated, has a violation -> "Does Not Support"
        writeAxeArtifact("page1_a11y.json", "color-contrast", "1.4.3");

        Path report = AccessibilityVpatReportGenerator.generate();
        assertTrue(report != null && Files.exists(report), "VPAT draft report should be generated");
        String html = Files.readString(report, StandardCharsets.UTF_8);

        assertTrue(html.contains("DRAFT"), "Should clearly mark the report as a draft");
        assertTrue(html.contains("not</b> an official VPAT"), "Should include the not-an-official-VPAT disclaimer");

        // 1.4.3 has a real finding -> Does Not Support
        assertTrue(html.contains("1.4.3"), "Evaluated criterion should appear");
        assertTrue(html.contains("Does Not Support"), "Criterion with a finding should be Does Not Support");

        // A criterion never seen in any artifact (e.g. 1.2.4 Captions) must be Not Evaluated,
        // never silently assumed to Support.
        assertTrue(html.contains("1.2.4"), "Standard WCAG criteria list should include uncovered criteria");
        assertTrue(html.contains("Not Evaluated"), "Uncovered criteria must be marked Not Evaluated, not Supports");
    }

    @Test
    void criterionWithNoObservedFindingsIsNotEvaluatedNotSupports() throws IOException {
        // Write a PASSING interaction artifact (empty issues array) for 2.4.7 (focus visible).
        // Since the loader only ever sees violations/issues (never "this criterion was
        // exercised and passed"), 2.4.7 must remain "Not Evaluated" -- proving we never
        // fabricate a "Supports" determination just because no violation happened to occur.
        writeInteractionArtifact("page1_interaction_focus-visible.json", "focus-not-visible", "2.4.7", true);

        Path report = AccessibilityVpatReportGenerator.generate();
        String html = Files.readString(report, StandardCharsets.UTF_8);

        int idx = html.indexOf("2.4.7");
        assertTrue(idx >= 0, "2.4.7 should still appear in the fixed WCAG criteria table");
        String rowSnippet = html.substring(idx, Math.min(html.length(), idx + 400));
        assertTrue(rowSnippet.contains("Not Evaluated"), "Criterion with zero observed findings must be Not Evaluated");
        assertFalse(rowSnippet.contains("Supports"), "Must never fabricate a Supports determination");
    }

    private void writeAxeArtifact(String fileName, String ruleId, String wcagCriterion) throws IOException {
        String json = "{"
                + "\"timestamp\":\"2026-01-01 10:00:00\","
                + "\"pageName\":\"Test Page\","
                + "\"outcome\":\"FAIL\","
                + "\"violationCount\":1,"
                + "\"violations\":[{"
                + "\"id\":\"" + ruleId + "\","
                + "\"impact\":\"serious\","
                + "\"wcagCriterion\":\"" + wcagCriterion + "\","
                + "\"description\":\"desc\",\"help\":\"help\",\"helpUrl\":\"https://example.com\","
                + "\"affectedElements\":[]"
                + "}]"
                + "}";
        Files.writeString(outputDir.resolve(fileName), json, StandardCharsets.UTF_8);
    }

    private void writeInteractionArtifact(String fileName, String ruleId, String wcagCriterion, boolean empty) throws IOException {
        String issuesJson = empty ? "[]" : ("[{\"ruleId\":\"" + ruleId + "\",\"wcagCriterion\":\"" + wcagCriterion + "\"}]");
        String json = "{"
                + "\"timestamp\":\"2026-01-01 10:00:00\","
                + "\"pageName\":\"Test Page\","
                + "\"checkId\":\"focus-visible\","
                + "\"outcome\":\"PASS\","
                + "\"issueCount\":0,"
                + "\"issues\":" + issuesJson
                + "}";
        Files.writeString(outputDir.resolve(fileName), json, StandardCharsets.UTF_8);
    }
}
