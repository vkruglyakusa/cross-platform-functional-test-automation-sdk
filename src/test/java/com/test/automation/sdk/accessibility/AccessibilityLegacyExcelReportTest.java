package com.test.automation.sdk.accessibility;

import com.deque.html.axecore.results.Rule;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the "Accessibility Legacy Excel Path Cleanup" correction.
 *
 * <p>Prior to this fix, {@code AccessibilityChecker.writeInteractionArtifact()} set a local
 * {@code engine} variable to the literal string {@code "NeedsReview"} whenever an interaction
 * finding required human confirmation, and reused that corrupted value for three purposes:
 * the legacy "All Issues" Excel sheet's Engine column, {@code deriveIssueType(impact, engine)},
 * and {@link AccessibilityFinding#fromInteractionIssue}. This conflated the independent
 * {@code engine} (originating analysis layer) and {@code findingType} (Violation / Needs
 * Review) dimensions, and leaked the corruption into the normalized
 * {@link AccessibilityFinding#engine} facade field as well — not just the legacy workbook.</p>
 *
 * <p>These tests drive the private {@code writeScanArtifact}/{@code writeInteractionArtifact}
 * entry points via reflection (there is no public, browser-free way to reach this in-memory,
 * live-updating legacy path) and verify the regenerated {@code accessibility-report.xlsx}
 * "All Issues" sheet, plus the {@link AccessibilityFinding} facade populated alongside it.</p>
 */
class AccessibilityLegacyExcelReportTest {

    private Path outputDir;

    @BeforeEach
    void setUp() throws Exception {
        outputDir = Files.createTempDirectory("a11y-legacy-excel-test");
        System.setProperty("reporting.accessibilityDir", outputDir.toString());
        System.setProperty("reporting.accessibilityWorkingDir", outputDir.toString());
        // This test drives the legacy, internal-only accessibility-report.xlsx directly,
        // which is opt-in (default false) — see AccessibilityChecker.writeExcelReport().
        System.setProperty("accessibility.reporting.legacyExcel", "true");
        AccessibilityChecker.resetFindings();
        clearInMemoryExcelRows();
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("reporting.accessibilityDir");
        System.clearProperty("reporting.accessibilityWorkingDir");
        System.clearProperty("accessibility.reporting.legacyExcel");
        AccessibilityChecker.resetFindings();
        clearInMemoryExcelRows();
    }

    @Test
    void confirmedAxeViolationProducesViolationIssueTypeWithRealEngineAndImpact() throws Exception {
        Rule violation = new Rule();
        violation.setId("color-contrast");
        violation.setDescription("Elements must meet minimum color contrast ratio thresholds");
        violation.setHelpUrl("https://dequeuniversity.com/rules/axe/4.10/color-contrast");
        violation.setImpact("serious");
        violation.setNodes(Collections.emptyList());

        invokeWriteScanArtifact("Home Page", "https://example.com/", new String[]{"wcag2aa"},
                Collections.singletonList(violation), Collections.emptyList(), "FAIL", null, null);

        Row row = findIssueRow(readAllIssuesSheet(), "color-contrast");
        assertNotNull(row, "Excel row for the axe violation should exist");
        assertEquals("SERIOUS", row.getCell(0).getStringCellValue(), "Impact column must carry the real severity");
        assertEquals("Violation", row.getCell(1).getStringCellValue(), "Issue Type must be 'Violation' for a confirmed axe violation");
        assertEquals("axe-core", row.getCell(9).getStringCellValue(), "Engine column must be the real originating engine");
    }

    @Test
    void needsReviewInteractionFindingProducesNeedsReviewIssueTypeWithRealEngineAndImpact() throws Exception {
        AccessibilityChecker.InteractionIssue issue = new AccessibilityChecker.InteractionIssue(
                "touch-target-size", "MODERATE", "WCAG 2.5.5",
                "Touch target may be smaller than the recommended minimum size",
                "<button>Tap</button>", "https://example.com/help/touch-target-size", true);

        invokeWriteInteractionArtifact("touch-target-size", "Home Page", Collections.singletonList(issue));

        Row row = findIssueRow(readAllIssuesSheet(), "touch-target-size");
        assertNotNull(row, "Excel row for the interaction finding should exist");
        assertEquals("MODERATE", row.getCell(0).getStringCellValue(), "Impact column must carry the real severity");
        assertEquals("Needs Review", row.getCell(1).getStringCellValue(), "Issue Type must be 'Needs Review'");
        assertEquals("Interaction", row.getCell(9).getStringCellValue(),
                "Engine column must remain the real originating engine, never the literal 'NeedsReview'");

        // The same fix must also apply to the normalized AccessibilityFinding facade populated
        // alongside the legacy Excel row — it must never receive the corrupted engine value either.
        List<AccessibilityFinding> findings = AccessibilityChecker.getFindings();
        assertEquals(1, findings.size());
        assertEquals("Interaction", findings.get(0).getEngine(),
                "AccessibilityFinding.engine must remain a real engine value, never 'NeedsReview'");
        assertEquals(AccessibilityFinding.CONFIDENCE_MANUAL_REVIEW, findings.get(0).getConfidence(),
                "AccessibilityFinding.confidence should independently carry the Needs Review classification");
    }

    @Test
    void confirmedInteractionViolationProducesViolationIssueType() throws Exception {
        AccessibilityChecker.InteractionIssue issue = new AccessibilityChecker.InteractionIssue(
                "keyboard-focus-trap", "CRITICAL", "WCAG 2.1.2",
                "Keyboard focus is trapped and cannot be moved away from this element",
                "<div role=\"dialog\">", "https://example.com/help/keyboard-focus-trap", false);

        invokeWriteInteractionArtifact("keyboard-focus-trap", "Home Page", Collections.singletonList(issue));

        Row row = findIssueRow(readAllIssuesSheet(), "keyboard-focus-trap");
        assertNotNull(row, "Excel row for the confirmed interaction violation should exist");
        assertEquals("Violation", row.getCell(1).getStringCellValue(),
                "A confirmed (non-needsReview) interaction finding must be 'Violation', not the old 'Interaction Check' bucket");
        assertEquals("Interaction", row.getCell(9).getStringCellValue());
    }

    @Test
    void findingTypeIsIndependentFromEngineAndImpact() throws Exception {
        AccessibilityChecker.InteractionIssue issue = new AccessibilityChecker.InteractionIssue(
                "zoom-reflow", "MINOR", "WCAG 1.4.10",
                "Content should reflow without loss of information at high zoom levels",
                "<main>", "https://example.com/help/zoom-reflow", true);

        invokeWriteInteractionArtifact("zoom-reflow", "Home Page", Collections.singletonList(issue));

        Row row = findIssueRow(readAllIssuesSheet(), "zoom-reflow");
        assertNotNull(row);
        String impact = row.getCell(0).getStringCellValue();
        String issueType = row.getCell(1).getStringCellValue();
        String engine = row.getCell(9).getStringCellValue();

        assertNotEquals(issueType, engine, "Finding Type must never equal Engine");
        assertNotEquals(issueType, impact, "Finding Type must never equal Impact");
        assertEquals("MINOR", impact);
        assertEquals("Needs Review", issueType);
        assertEquals("Interaction", engine);
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private XSSFSheet readAllIssuesSheet() throws Exception {
        Path excelPath = outputDir.resolve("accessibility-report.xlsx");
        assertTrue(Files.exists(excelPath), "Legacy accessibility-report.xlsx should be regenerated");
        try (InputStream in = Files.newInputStream(excelPath);
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            XSSFSheet sheet = wb.getSheet("All Issues");
            assertNotNull(sheet, "'All Issues' sheet should exist");
            // Copy rows out before the workbook (and its underlying stream) is closed.
            XSSFWorkbook copy = new XSSFWorkbook();
            XSSFSheet copySheet = copy.createSheet("All Issues");
            for (Row row : sheet) {
                Row newRow = copySheet.createRow(row.getRowNum());
                for (org.apache.poi.ss.usermodel.Cell cell : row) {
                    org.apache.poi.ss.usermodel.Cell newCell = newRow.createCell(cell.getColumnIndex());
                    if (cell.getCellTypeEnum() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        newCell.setCellValue(cell.getNumericCellValue());
                    } else {
                        newCell.setCellValue(cell.getStringCellValue());
                    }
                }
            }
            return copySheet;
        }
    }

    private Row findIssueRow(XSSFSheet sheet, String ruleId) {
        for (Row row : sheet) {
            org.apache.poi.ss.usermodel.Cell cell = row.getCell(5); // "Rule ID" column
            if (cell != null && cell.getCellTypeEnum() == org.apache.poi.ss.usermodel.CellType.STRING
                    && ruleId.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        return null;
    }

    private void invokeWriteScanArtifact(String pageName, String pageUrl, String[] tags,
                                          List<Rule> violations, List<Rule> incomplete,
                                          String outcome, String errorMessage, Object scopeInfo) throws Exception {
        Class<?> scopeInfoClass = Class.forName(
                "com.test.automation.sdk.accessibility.AccessibilityChecker$ScanScopeInfo");
        Method m = AccessibilityChecker.class.getDeclaredMethod("writeScanArtifact",
                String.class, String.class, String[].class, List.class, List.class,
                String.class, String.class, scopeInfoClass);
        m.setAccessible(true);
        m.invoke(null, pageName, pageUrl, tags, violations, incomplete, outcome, errorMessage, scopeInfo);
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
    }
}
