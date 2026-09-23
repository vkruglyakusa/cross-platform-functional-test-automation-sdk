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
 * Focused regression coverage for the "Needs Review" model correction: {@code findingType}
 * ({@code VIOLATION}/{@code NEEDS_REVIEW}) is an independent classification dimension from
 * {@code impact} (severity) and {@code engine} (axe-core/Interaction). Prior to this
 * correction, {@code impact} was overloaded with the string {@code "NEEDS_REVIEW"}, which
 * conflated two independent dimensions and made needs-review findings invisible in the
 * hardcoded 5-bucket ({@code CRITICAL}/{@code SERIOUS}/{@code MODERATE}/{@code MINOR}/
 * {@code UNKNOWN}) impact drilldown.
 */
class AccessibilityFindingModelTest {

    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        outputDir = Files.createTempDirectory("a11y-finding-model-test");
        System.setProperty("reporting.accessibilityDir", outputDir.toString());
        System.setProperty("reporting.accessibilityWorkingDir", outputDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("reporting.accessibilityDir");
        System.clearProperty("reporting.accessibilityWorkingDir");
    }

    @Test
    void axeViolationsMapToFindingTypeViolation() throws IOException {
        writeAxeArtifactWithIncomplete("page1_a11y.json",
                /* violation impact */ "serious", /* incomplete impact */ null);

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        String html = Files.readString(htmlReport, StandardCharsets.UTF_8);

        assertTrue(html.contains("data-finding-type='VIOLATION'") || html.contains("data-finding-type=\"VIOLATION\""),
                "axe-core 'violations' entries should be tagged findingType=VIOLATION");
    }

    @Test
    void axeIncompleteMapsToFindingTypeNeedsReviewWithRealImpactPreserved() throws IOException {
        writeAxeArtifactWithIncomplete("page1_a11y.json",
                /* violation impact */ "serious", /* incomplete impact */ "moderate");

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        String html = Files.readString(htmlReport, StandardCharsets.UTF_8);

        // axe "incomplete" -> findingType=NEEDS_REVIEW
        assertTrue(html.contains("data-finding-type='NEEDS_REVIEW'") || html.contains("data-finding-type=\"NEEDS_REVIEW\""),
                "axe-core 'incomplete' entries should be tagged findingType=NEEDS_REVIEW");
        // The real impact/severity metadata (MODERATE) must be preserved independently —
        // never collapsed into the literal string "NEEDS_REVIEW".
        assertTrue(html.contains("MODERATE"), "Incomplete rule's real impact (MODERATE) should be preserved");
        assertTrue(!html.replace("Needs Review", "").contains("impact:'needs_review'"),
                "impact must never be overwritten with the needs_review token");
    }

    @Test
    void findingTypeIsIndependentOfImpact() throws IOException {
        // findingType=NEEDS_REVIEW with impact=SERIOUS must be representable and preserved,
        // i.e. NEEDS_REVIEW does not require (or imply) any particular impact value.
        writeInteractionArtifact("page1_interaction_touch-target-size.json",
                "touch-target-size", true, "serious");

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        String html = Files.readString(htmlReport, StandardCharsets.UTF_8);

        assertTrue(html.contains("data-finding-type='NEEDS_REVIEW'") || html.contains("data-finding-type=\"NEEDS_REVIEW\""),
                "Interaction finding with needsReview=true should be findingType=NEEDS_REVIEW");
        assertTrue(html.contains("pill-serious"),
                "The real impact (SERIOUS) must still be rendered via its own impact pill, independent of findingType");
    }

    @Test
    void engineAndFindingTypeAreIndependentDimensions() throws IOException {
        writeAxeArtifactWithIncomplete("page1_a11y.json", "serious", "moderate");
        writeInteractionArtifact("page1_interaction_keyboard-navigation.json",
                "keyboard-navigation", false, "moderate");
        writeInteractionArtifact("page1_interaction_touch-target-size.json",
                "touch-target-size", true, "minor");

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        String html = Files.readString(htmlReport, StandardCharsets.UTF_8);

        // Engine Filter still exposes exactly 2 chips (axe-core / Interaction) — a 3rd
        // "NeedsReview" engine chip must not be restored; Needs Review is represented
        // exclusively via the Finding Type filter dimension.
        assertTrue(html.contains("id='a11yEngineFilter'"), "Engine Filter should render");
        assertTrue(html.contains("value='axe-core'"), "Engine Filter should offer axe-core");
        assertTrue(html.contains("value='Interaction'"), "Engine Filter should offer Interaction");
        assertTrue(!html.contains("value='NeedsReview'"), "Engine Filter must not restore a 3rd NeedsReview chip");

        // Finding Type Filter is its own independent dimension.
        assertTrue(html.contains("id='a11yFindingTypeFilter'"), "Finding Type Filter should render");
        assertTrue(html.contains("value='VIOLATION'"), "Finding Type Filter should offer Violation");
        assertTrue(html.contains("value='NEEDS_REVIEW'"), "Finding Type Filter should offer Needs Review");

        // Combinable: an axe-core NEEDS_REVIEW finding and an Interaction NEEDS_REVIEW
        // finding must both be independently taggable/filterable.
        assertTrue(html.contains("data-engine='axe-core' data-finding-type='NEEDS_REVIEW'")
                        || html.contains("data-engine=\"axe-core\" data-finding-type=\"NEEDS_REVIEW\""),
                "axe-core engine + NEEDS_REVIEW finding type should combine independently");
    }

    @Test
    void htmlKpiExposesNeedsReviewCountDerivedFromFindingType() throws IOException {
        writeAxeArtifactWithIncomplete("page1_a11y.json", "serious", "moderate");

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        String html = Files.readString(htmlReport, StandardCharsets.UTF_8);

        assertTrue(html.contains("kpi-needs-review"), "A Needs Review KPI card should be present");
        assertTrue(html.contains("Needs Review"), "KPI label 'Needs Review' should be present");
    }

    @Test
    void excelExposesIndependentEngineFindingTypeAndImpactColumns() throws IOException {
        writeAxeArtifactWithIncomplete("page1_a11y.json", "serious", "moderate");
        writeInteractionArtifact("page1_interaction_touch-target-size.json",
                "touch-target-size", true, "minor");

        Path excelReport = AccessibilityExcelReporter.generate();
        assertTrue(excelReport != null && Files.exists(excelReport), "Excel report should be generated");

        try (InputStream in = Files.newInputStream(excelReport);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            XSSFSheet sheet = wb.getSheet("Violations Detail");
            assertNotNull(sheet, "Violations Detail sheet should exist");

            Row header = findHeaderRow(sheet, "Rule ID");
            assertNotNull(header, "Should locate the Violations Detail header row");
            int lastCol = header.getLastCellNum() - 1;
            assertEquals("Finding Type", header.getCell(lastCol).getStringCellValue());
            assertEquals("Engine", header.getCell(lastCol - 1).getStringCellValue());

            // Find "Finding Type" and "Impact" column indices, then verify at least one
            // row exists with findingType=NEEDS_REVIEW and a real (non-"NEEDS_REVIEW")
            // impact value — proving the two columns vary independently.
            int findingTypeCol = -1;
            int impactCol = -1;
            for (int c = 0; c <= lastCol; c++) {
                String h = header.getCell(c).getStringCellValue();
                if ("Finding Type".equals(h)) findingTypeCol = c;
                if ("Impact".equals(h)) impactCol = c;
            }
            assertTrue(findingTypeCol >= 0 && impactCol >= 0, "Finding Type and Impact columns must both exist");

            boolean foundIndependentNeedsReviewRow = false;
            for (Row row : sheet) {
                if (row.getRowNum() <= header.getRowNum()) continue;
                org.apache.poi.ss.usermodel.Cell ftCell = row.getCell(findingTypeCol);
                org.apache.poi.ss.usermodel.Cell impCell = row.getCell(impactCol);
                if (ftCell == null || impCell == null) continue;
                if ("NEEDS_REVIEW".equals(ftCell.getStringCellValue())
                        && !"NEEDS_REVIEW".equals(impCell.getStringCellValue())) {
                    foundIndependentNeedsReviewRow = true;
                    break;
                }
            }
            assertTrue(foundIndependentNeedsReviewRow,
                    "At least one row should have findingType=NEEDS_REVIEW with a real, independent impact value");
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

    private void writeAxeArtifactWithIncomplete(String fileName, String violationImpact, String incompleteImpact)
            throws IOException {
        StringBuilder json = new StringBuilder("{"
                + "\"timestamp\":\"2026-01-01 10:00:00\","
                + "\"pageName\":\"Test Page\","
                + "\"outcome\":\"FAIL\","
                + "\"violationCount\":1,"
                + "\"violations\":[{"
                + "\"id\":\"color-contrast\","
                + "\"impact\":\"" + violationImpact + "\","
                + "\"description\":\"Elements must meet minimum color contrast ratio\","
                + "\"help\":\"Ensure sufficient color contrast\","
                + "\"helpUrl\":\"https://dequeuniversity.com/rules/axe/color-contrast\","
                + "\"affectedElements\":[\"<span class='swatch'></span>\"]"
                + "}]");
        if (incompleteImpact != null) {
            json.append(",\"incompleteCount\":1,\"incomplete\":[{"
                    + "\"id\":\"aria-hidden-focus\","
                    + "\"impact\":\"" + incompleteImpact + "\","
                    + "\"description\":\"ARIA hidden element must not be focusable\","
                    + "\"help\":\"Ensure aria-hidden elements are not focusable\","
                    + "\"helpUrl\":\"https://dequeuniversity.com/rules/axe/aria-hidden-focus\","
                    + "\"affectedElements\":[\"<div aria-hidden='true'><button></button></div>\"]"
                    + "}]");
        }
        json.append("}");
        Files.writeString(outputDir.resolve(fileName), json.toString(), StandardCharsets.UTF_8);
    }

    private void writeInteractionArtifact(String fileName, String checkId, boolean needsReview, String impact)
            throws IOException {
        String issueRuleId = checkId + "-issue";
        String json = "{"
                + "\"timestamp\":\"2026-01-01 10:00:05\","
                + "\"pageName\":\"Test Page\","
                + "\"outcome\":\"FAIL\","
                + "\"checkId\":\"" + checkId + "\","
                + "\"issueCount\":1,"
                + "\"issues\":[{"
                + "\"ruleId\":\"" + issueRuleId + "\","
                + "\"impact\":\"" + impact + "\","
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
