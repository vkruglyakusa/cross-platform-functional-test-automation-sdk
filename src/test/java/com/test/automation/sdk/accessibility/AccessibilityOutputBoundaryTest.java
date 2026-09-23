package com.test.automation.sdk.accessibility;

import com.deque.html.axecore.results.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the "Accessibility Report Output Boundary Cleanup" correction.
 *
 * <p>Azure DevOps' {@code PublishBuildArtifacts@1} step publishes the entire {@code test-output}
 * tree wholesale and must not be narrowed. Because of that, accessibility must enforce its own
 * publication boundary: raw/intermediate scan artifacts belong in an internal working directory
 * ({@link com.test.automation.sdk.accessibility.config.A11yConfig#workingDir()}, default
 * {@code target/accessibility-work}) that lives OUTSIDE {@code test-output}, while only the
 * final, authoritative reports are written to the published directory
 * ({@link com.test.automation.sdk.accessibility.config.A11yConfig#outputDir()}, default
 * {@code test-output/accessibility}).</p>
 *
 * <p>These tests exercise the real filesystem output with two <b>physically distinct</b>
 * directories (unlike other accessibility tests, which point both at the same temp directory
 * for simplicity) so that a regression that accidentally writes an internal artifact into the
 * published tree — or a final report into the working tree — is caught.</p>
 */
class AccessibilityOutputBoundaryTest {

    /** Only these published-directory filename patterns are part of the supported report contract. */
    private static final List<Pattern> ALLOWED_PUBLISHED_PATTERNS = List.of(
            Pattern.compile("^accessibility-report_.*\\.xlsx$"),
            Pattern.compile("^accessibility-summary\\.html$"),
            Pattern.compile("^accessibility-vpat-draft\\.html$")
    );

    private Path workingDir;
    private Path publishedDir;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Files.createTempDirectory("a11y-output-boundary-test");
        workingDir = root.resolve("target").resolve("accessibility-work");
        publishedDir = root.resolve("test-output").resolve("accessibility");
        Files.createDirectories(workingDir);
        Files.createDirectories(publishedDir);

        System.setProperty("reporting.accessibilityWorkingDir", workingDir.toString());
        System.setProperty("reporting.accessibilityDir", publishedDir.toString());
        System.setProperty("accessibility.reporting.legacyExcel", "true");
        AccessibilityChecker.resetFindings();
        clearInMemoryExcelRows();
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("reporting.accessibilityWorkingDir");
        System.clearProperty("reporting.accessibilityDir");
        System.clearProperty("accessibility.reporting.legacyExcel");
        AccessibilityChecker.resetFindings();
        clearInMemoryExcelRows();
    }

    @Test
    void rawAxeScanArtifactIsWrittenToWorkingDirectoryNotPublished() throws Exception {
        Rule violation = new Rule();
        violation.setId("color-contrast");
        violation.setImpact("serious");
        violation.setNodes(Collections.emptyList());

        invokeWriteScanArtifact("Home Page", "https://example.com/", new String[]{"wcag2aa"},
                Collections.singletonList(violation), Collections.emptyList(), "FAIL", null, null);

        assertTrue(hasFileMatching(workingDir, ".*_a11y\\.json$"),
                "Raw axe JSON must be written to the working directory");
        assertFalse(hasFileMatching(publishedDir, ".*_a11y\\.json$"),
                "Raw axe JSON must never appear in the published directory");
    }

    @Test
    void interactionArtifactIsWrittenToWorkingDirectoryNotPublished() throws Exception {
        AccessibilityChecker.InteractionIssue issue = new AccessibilityChecker.InteractionIssue(
                "touch-target-size", "MODERATE", "WCAG 2.5.5",
                "Touch target may be smaller than the recommended minimum size",
                "<button>Tap</button>", "https://example.com/help/touch-target-size", true);

        invokeWriteInteractionArtifact("touch-target-size", "Home Page", Collections.singletonList(issue));

        assertTrue(hasFileMatching(workingDir, ".*_interaction_.*\\.json$"),
                "Interaction JSON must be written to the working directory");
        assertFalse(hasFileMatching(publishedDir, ".*_interaction_.*\\.json$"),
                "Interaction JSON must never appear in the published directory");
    }

    @Test
    void jsonlSummaryRollupStaysInWorkingDirectory() throws Exception {
        Rule violation = new Rule();
        violation.setId("color-contrast");
        violation.setImpact("serious");
        violation.setNodes(Collections.emptyList());

        invokeWriteScanArtifact("Home Page", "https://example.com/", new String[]{"wcag2aa"},
                Collections.singletonList(violation), Collections.emptyList(), "FAIL", null, null);

        assertTrue(Files.exists(workingDir.resolve("accessibility-summary.jsonl")),
                "accessibility-summary.jsonl must be written to the working directory");
        assertFalse(Files.exists(publishedDir.resolve("accessibility-summary.jsonl")),
                "accessibility-summary.jsonl must never appear in the published directory");
    }

    @Test
    void legacyWorkbookStaysInWorkingDirectoryNotPublished() throws Exception {
        AccessibilityChecker.InteractionIssue issue = new AccessibilityChecker.InteractionIssue(
                "keyboard-focus-trap", "CRITICAL", "WCAG 2.1.2",
                "Keyboard focus is trapped and cannot be moved away from this element",
                "<div role=\"dialog\">", "https://example.com/help/keyboard-focus-trap", false);

        invokeWriteInteractionArtifact("keyboard-focus-trap", "Home Page", Collections.singletonList(issue));

        assertTrue(Files.exists(workingDir.resolve("accessibility-report.xlsx")),
                "Legacy accessibility-report.xlsx (internal artifact) must be generated in the working directory");
        assertFalse(Files.exists(publishedDir.resolve("accessibility-report.xlsx")),
                "Legacy accessibility-report.xlsx must never be published");
    }

    @Test
    void modernHtmlAndExcelReportsAreWrittenToPublishedDirectory() throws Exception {
        seedOneScanArtifact();

        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        Path excelReport = AccessibilityExcelReporter.generate();
        Path vpatReport = AccessibilityVpatReportGenerator.generate();

        assertTrue(htmlReport != null && htmlReport.startsWith(publishedDir),
                "Modern HTML summary report must be written under the published directory");
        assertTrue(excelReport != null && excelReport.startsWith(publishedDir),
                "Modern Excel report must be written under the published directory");
        assertTrue(vpatReport != null && vpatReport.startsWith(publishedDir),
                "VPAT draft report must be written under the published directory");

        assertFalse(htmlReport.getParent().equals(workingDir), "HTML report parent must not be the working dir");
        assertFalse(excelReport.getParent().equals(workingDir), "Excel report parent must not be the working dir");
    }

    @Test
    void reportGeneratorsSuccessfullyConsumeRawArtifactsFromWorkingDirectory() throws Exception {
        seedOneScanArtifact();

        // The generators must find and use the raw artifacts placed in workingDir even
        // though the published directory is a completely separate, initially-empty tree.
        Path htmlReport = AccessibilitySummaryReportGenerator.generate();
        assertTrue(htmlReport != null && Files.exists(htmlReport));
        String html = Files.readString(htmlReport);
        assertTrue(html.contains("color-contrast") || html.toLowerCase().contains("home page"),
                "Generated HTML report should reflect data sourced from the working directory");
    }

    @Test
    void publishedDirectoryContainsOnlyAllowlistedReportFilenames() throws Exception {
        seedOneScanArtifact();
        AccessibilitySummaryReportGenerator.generate();
        AccessibilityExcelReporter.generate();
        AccessibilityVpatReportGenerator.generate();

        try (Stream<Path> files = Files.list(publishedDir)) {
            List<Path> unexpected = files
                    .filter(Files::isRegularFile)
                    .filter(p -> ALLOWED_PUBLISHED_PATTERNS.stream()
                            .noneMatch(pattern -> pattern.matcher(p.getFileName().toString()).matches()))
                    .collect(Collectors.toList());
            assertTrue(unexpected.isEmpty(),
                    "Published directory must only contain allow-listed report files, found: " + unexpected);
        }
    }

    @Test
    void publishedDirectoryRejectsLegacyAndRawFilenamePatterns() {
        // Sanity-check the allowlist itself rejects the exact filenames that must never
        // regress back into the published tree.
        String[] forbidden = {
                "accessibility-report.xlsx",
                "20260101_120000_Home_Page_a11y.json",
                "20260101_120000_Home_Page_interaction_touch-target-size.json",
                "accessibility-summary.jsonl"
        };
        for (String name : forbidden) {
            boolean matchesAllowlist = ALLOWED_PUBLISHED_PATTERNS.stream()
                    .anyMatch(pattern -> pattern.matcher(name).matches());
            assertFalse(matchesAllowlist, "Forbidden filename must not match the published allowlist: " + name);
        }
    }

    @Test
    void accessibilityReportingExcelFalseSuppressesModernExcelReportOnly() throws Exception {
        System.setProperty("accessibility.reporting.excel", "false");
        try {
            seedOneScanArtifact();
            assertFalse(com.test.automation.sdk.accessibility.report.A11yReporterFactory.isExcelEnabled(),
                    "isExcelEnabled() must reflect accessibility.reporting.excel=false");
            // The legacy internal workbook is independently gated by legacyExcel and must still
            // exist in the working dir (it does not respect accessibility.reporting.excel).
            assertTrue(Files.exists(workingDir.resolve("accessibility-report.xlsx")));
        } finally {
            System.clearProperty("accessibility.reporting.excel");
        }
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private void seedOneScanArtifact() throws Exception {
        Rule violation = new Rule();
        violation.setId("color-contrast");
        violation.setDescription("Elements must meet minimum color contrast ratio thresholds");
        violation.setHelpUrl("https://dequeuniversity.com/rules/axe/4.10/color-contrast");
        violation.setImpact("serious");
        violation.setNodes(Collections.emptyList());
        invokeWriteScanArtifact("Home Page", "https://example.com/", new String[]{"wcag2aa"},
                Collections.singletonList(violation), Collections.emptyList(), "FAIL", null, null);
    }

    private boolean hasFileMatching(Path dir, String regex) throws Exception {
        Pattern pattern = Pattern.compile(regex);
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.anyMatch(p -> pattern.matcher(p.getFileName().toString()).matches());
        }
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
