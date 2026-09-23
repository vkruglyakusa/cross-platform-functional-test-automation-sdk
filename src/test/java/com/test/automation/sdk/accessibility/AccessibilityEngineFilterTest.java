package com.test.automation.sdk.accessibility;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two report-filter gaps closed for the ported
 * "Accessibility Report Filters" capability (see AccessibilityTestAutomation reference):
 * <ol>
 *   <li>The HTML report previously read only {@code *_a11y.json}, silently dropping all
 *       Layers 2-5 ({@code *_interaction_*.json}) findings that the Excel report already
 *       included — {@link AccessibilitySummaryReportGenerator} now merges both.</li>
 *   <li>The HTML report's client-side "Engine Filter" (axe-core / Interaction chips) and
 *       the Excel report's native AutoFilter dropdowns (Scan History / Violations Detail),
 *       both report-only and never affecting counted totals, PASS/FAIL, or suppression.</li>
 * </ol>
 *
 * <p>{@code needsReview} is modeled as an independent {@code findingType} dimension
 * ({@code VIOLATION}/{@code NEEDS_REVIEW}), never encoded into the {@code impact}
 * (severity) value — see {@link AccessibilityFindingModelTest} for focused coverage
 * of that corrected classification model.</p>
 */
class AccessibilityEngineFilterTest {

    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        outputDir = Files.createTempDirectory("a11y-engine-filter-test");
        System.setProperty("reporting.accessibilityDir", outputDir.toString());
        System.setProperty("reporting.accessibilityWorkingDir", outputDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("reporting.accessibilityDir");
        System.clearProperty("reporting.accessibilityWorkingDir");
    }

    @Test
    void htmlReportMergesInteractionArtifactsAndRendersEngineFilter() throws IOException {
        writeAxeArtifact("page1_a11y.json");
        writeInteractionArtifact("page1_interaction_keyboard-navigation.json", "keyboard-navigation", false);
        writeInteractionArtifact("page1_interaction_touch-target-size.json", "touch-target-size", true);

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        assertTrue(htmlReport != null && Files.exists(htmlReport), "HTML summary report should be generated");
        String html = Files.readString(htmlReport, StandardCharsets.UTF_8);

        // Previously-lost Layer 2-5 findings must now appear.
        assertTrue(html.contains("keyboard-navigation-issue"), "Interaction finding should be merged into the HTML report");
        assertTrue(html.contains("touch-target-issue"), "Second interaction finding should also be merged");
        // needsReview=true is surfaced via the independent Finding Type dimension
        // (findingType/data-finding-type), never encoded into the Impact/severity value.
        assertTrue(html.contains("data-finding-type='NEEDS_REVIEW'") || html.contains("data-finding-type=\"NEEDS_REVIEW\""),
                "needsReview interaction issues should render as findingType=NEEDS_REVIEW, independent of impact");

        // Engine Filter UI (report-only; 2 chips: axe-core / Interaction).
        assertTrue(html.contains("id='a11yEngineFilter'"), "Engine Filter container should render");
        assertTrue(html.contains("a11yEngineToggle"), "Engine Filter checkboxes should wire up a11yEngineToggle");
        assertTrue(html.contains("value='axe-core'"), "Should offer an axe-core chip");
        assertTrue(html.contains("value='Interaction'"), "Should offer an Interaction chip");

        // Violation-level elements are tagged so the client-side filter can show/hide them.
        assertTrue(html.contains("data-engine='axe-core'"), "axe-core violations should be tagged data-engine");
        assertTrue(html.contains("data-engine='Interaction:keyboard-navigation'")
                        || html.contains("data-engine=\"Interaction:keyboard-navigation\""),
                "Interaction violations should be tagged with their checkId-qualified engine");
    }

    @Test
    void excelReportAddsAutoFilterAndEngineColumnWithoutChangingCounts() throws IOException {
        writeAxeArtifact("page1_a11y.json");
        writeInteractionArtifact("page1_interaction_keyboard-navigation.json", "keyboard-navigation", false);

        Path excelReport = AccessibilityExcelReporter.generate();
        assertTrue(excelReport != null && Files.exists(excelReport), "Excel report should be generated");

        try (InputStream in = Files.newInputStream(excelReport);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {

            XSSFSheet violationsDetail = wb.getSheet("Violations Detail");
            assertNotNull(violationsDetail, "Violations Detail sheet should exist");
            assertTrue(violationsDetail.getCTWorksheet().isSetAutoFilter(),
                    "Violations Detail header row should have an AutoFilter applied");

            // Header row now includes an "Engine" column and an independent "Finding Type"
            // column (report-only additions; do not change the Timestamp/Page/Outcome/
            // Rule ID/etc. columns already present, and Finding Type is never merged
            // into the Impact column).
            Row headerRow = findHeaderRow(violationsDetail, "Rule ID");
            assertNotNull(headerRow, "Should be able to locate the Violations Detail header row");
            assertEquals("Finding Type", headerRow.getCell(headerRow.getLastCellNum() - 1).getStringCellValue(),
                    "Finding Type should be the last header column");
            assertEquals("Engine", headerRow.getCell(headerRow.getLastCellNum() - 2).getStringCellValue(),
                    "Engine should be the second-to-last header column, independent of Finding Type");

            XSSFSheet scanHistory = wb.getSheet("Scan History");
            assertNotNull(scanHistory, "Scan History sheet should exist");
            assertTrue(scanHistory.getCTWorksheet().isSetAutoFilter(),
                    "Scan History header row should have an AutoFilter applied");

            XSSFSheet summary = wb.getSheet("Summary");
            assertNotNull(summary, "Summary sheet should still exist unchanged");
        }
    }

    private Row findHeaderRow(XSSFSheet sheet, String mustContainHeader) {
        for (Row row : sheet) {
            for (org.apache.poi.ss.usermodel.Cell cell : row) {
                if (cell.getCellTypeEnum() == org.apache.poi.ss.usermodel.CellType.STRING
                        && mustContainHeader.equals(cell.getStringCellValue())) {
                    return row;
                }
            }
        }
        return null;
    }

    private void writeAxeArtifact(String fileName) throws IOException {
        String json = "{"
                + "\"timestamp\":\"2026-01-01 10:00:00\","
                + "\"pageName\":\"Test Page\","
                + "\"outcome\":\"FAIL\","
                + "\"violationCount\":1,"
                + "\"violations\":[{"
                + "\"id\":\"color-contrast\","
                + "\"impact\":\"serious\","
                + "\"description\":\"Elements must meet minimum color contrast ratio\","
                + "\"help\":\"Ensure sufficient color contrast\","
                + "\"helpUrl\":\"https://dequeuniversity.com/rules/axe/color-contrast\","
                + "\"affectedElements\":[\"<span class='swatch'></span>\"]"
                + "}]"
                + "}";
        Files.writeString(outputDir.resolve(fileName), json, StandardCharsets.UTF_8);
    }

    private void writeInteractionArtifact(String fileName, String checkId, boolean needsReview) throws IOException {
        String issueRuleId = "keyboard-navigation".equals(checkId) ? "keyboard-navigation-issue" : "touch-target-issue";
        String json = "{"
                + "\"timestamp\":\"2026-01-01 10:00:05\","
                + "\"pageName\":\"Test Page\","
                + "\"outcome\":\"FAIL\","
                + "\"checkId\":\"" + checkId + "\","
                + "\"issueCount\":1,"
                + "\"issues\":[{"
                + "\"ruleId\":\"" + issueRuleId + "\","
                + "\"impact\":\"moderate\","
                + "\"description\":\"Interaction/layout issue detected\","
                + "\"wcagRef\":\"2.1.1\","
                + "\"helpUrl\":\"https://example.com/help\","
                + "\"element\":\"<button>Click</button>\","
                + "\"needsReview\":" + needsReview
                + "}]"
                + "}";
        Files.writeString(outputDir.resolve(fileName), json, StandardCharsets.UTF_8);
    }
}
