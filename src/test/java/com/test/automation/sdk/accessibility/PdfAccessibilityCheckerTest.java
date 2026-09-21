package com.test.automation.sdk.accessibility;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link PdfAccessibilityChecker}'s four structural checks (tagging, title,
 * language, image alt text) against minimal PDFs built directly with PDFBox, and that
 * findings are written using the existing {@code *_interaction_*.json} artifact schema
 * so the Excel/HTML/VPAT reporters pick them up automatically.
 */
class PdfAccessibilityCheckerTest {

    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        outputDir = Files.createTempDirectory("a11y-pdf-test");
        System.setProperty("reporting.accessibilityDir", outputDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("reporting.accessibilityDir");
    }

    @Test
    void untaggedPdfWithNoMetadataReportsAllDocumentLevelFindings() throws IOException {
        Path pdf = outputDir.resolve("untagged.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            // No MarkInfo, no StructureTreeRoot, no Title, no Language -- the "worst case".
            doc.save(pdf.toFile());
        }

        List<AccessibilityFinding> findings = PdfAccessibilityChecker.check(pdf, "Untagged Doc");

        Set<String> ruleIds = findings.stream().map(AccessibilityFinding::getRuleId).collect(Collectors.toSet());
        assertTrue(ruleIds.contains("pdf-tagged"), "Untagged PDF should fail the tagging check");
        assertTrue(ruleIds.contains("pdf-document-title"), "PDF with no title should fail the title check");
        assertTrue(ruleIds.contains("pdf-document-language"), "PDF with no language should fail the language check");
        // Image alt-text check is skipped entirely for untagged documents (nothing to walk).
        assertTrue(ruleIds.stream().noneMatch(id -> id.equals("pdf-image-alt-text")));

        // Verify the artifact was written using the standard interaction-artifact schema.
        List<Path> artifacts;
        try (var stream = Files.list(outputDir)) {
            artifacts = stream.filter(p -> p.getFileName().toString().contains("_interaction_pdf-ua")).toList();
        }
        assertEquals(1, artifacts.size(), "Should write exactly one pdf-ua interaction artifact");
        String json = Files.readString(artifacts.get(0));
        assertTrue(json.contains("\"checkId\":\"pdf-ua\""));
        assertTrue(json.contains("\"outcome\":\"FAIL\""));
    }

    @Test
    void taggedPdfWithTitleLanguageAndAltTextIsClean() throws IOException {
        Path pdf = outputDir.resolve("tagged.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());

            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            catalog.setMarkInfo(markInfo);
            catalog.setLanguage("en-US");

            PDStructureTreeRoot structureTreeRoot = new PDStructureTreeRoot();
            PDStructureElement figure = new PDStructureElement("Figure", structureTreeRoot);
            figure.setAlternateDescription("A descriptive chart of quarterly results");
            structureTreeRoot.appendKid(figure);
            catalog.setStructureTreeRoot(structureTreeRoot);

            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Quarterly Report");
            doc.setDocumentInformation(info);

            doc.save(pdf.toFile());
        }

        List<AccessibilityFinding> findings = PdfAccessibilityChecker.check(pdf, "Clean Doc");

        assertTrue(findings.isEmpty(), "Tagged PDF with title, language, and alt text should have zero findings: " + findings);
    }

    @Test
    void taggedPdfWithFigureMissingAltTextIsFlagged() throws IOException {
        Path pdf = outputDir.resolve("missing-alt.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());

            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            catalog.setMarkInfo(markInfo);
            catalog.setLanguage("en-US");

            PDStructureTreeRoot structureTreeRoot = new PDStructureTreeRoot();
            PDStructureElement figure = new PDStructureElement("Figure", structureTreeRoot);
            // No alternate description, no actual text -- must be flagged.
            structureTreeRoot.appendKid(figure);
            catalog.setStructureTreeRoot(structureTreeRoot);

            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Report With Bad Image");
            doc.setDocumentInformation(info);

            doc.save(pdf.toFile());
        }

        List<AccessibilityFinding> findings = PdfAccessibilityChecker.check(pdf, "Bad Image Doc");

        assertEquals(1, findings.size(), "Only the missing-alt-text figure should be flagged: " + findings);
        assertEquals("pdf-image-alt-text", findings.get(0).getRuleId());
        assertEquals("1.1.1", findings.get(0).getWcagCriterion());
    }
}
