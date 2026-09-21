package com.test.automation.sdk.accessibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.test.automation.sdk.accessibility.config.A11yConfig;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF/UA-lite accessibility checker: inspects a PDF document's tagging structure
 * directly via Apache PDFBox (already an SDK dependency) and reports findings using
 * the same normalized {@link AccessibilityFinding} shape as every other engine.
 *
 * <h3>Scope -- what this checks, and what it does not</h3>
 * <p>This is a targeted, well-understood set of structural checks, not a full PDF/UA
 * conformance validator (a true validator like veraPDF checks hundreds of machine
 * rules and still cannot replace human review of reading order or alt-text quality).
 * What is checked here:</p>
 * <ul>
 *   <li><b>pdf-tagged</b> (WCAG 1.3.1) -- the document declares itself as a Tagged PDF
 *       ({@code MarkInfo/Marked}) and has a structure tree root at all. An untagged PDF
 *       cannot be read reliably by assistive technology, regardless of anything else.</li>
 *   <li><b>pdf-document-title</b> (WCAG 2.4.2) -- the document metadata has a non-blank
 *       Title (read by screen readers when the file/tab is announced).</li>
 *   <li><b>pdf-document-language</b> (WCAG 3.1.1) -- the document catalog declares a
 *       primary language, so assistive technology uses correct pronunciation rules.</li>
 *   <li><b>pdf-image-alt-text</b> (WCAG 1.1.1) -- every {@code Figure}-tagged structure
 *       element has either an alternate description or actual-text replacement. Only
 *       runs when the document is tagged (untagged PDFs already fail pdf-tagged and
 *       have no structure tree to walk).</li>
 * </ul>
 * <p>Not checked (requires manual review or a full PDF/UA validator): reading order
 * correctness, table header associations, form-field labeling, color contrast within
 * embedded content, and general PDF/UA machine-rule conformance.</p>
 *
 * <h3>Reporting integration</h3>
 * <p>Findings are written as a {@code *_interaction_pdf-ua.json} artifact, using the
 * exact same schema {@link AccessibilityChecker#writeInteractionArtifact} already
 * produces for the web Interaction/WCAG2.2/Structural/Motion layers. This means
 * {@link AccessibilityExcelReporter}, {@link AccessibilitySummaryReportGenerator}, and
 * {@link AccessibilityVpatReportGenerator} all pick up PDF findings automatically --
 * no changes were needed in any of those three generators.</p>
 */
public final class PdfAccessibilityChecker {

    private static final Logger logger = LoggerFactory.getLogger(PdfAccessibilityChecker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String CHECK_ID = "pdf-ua";
    /** Caps how many missing-alt-text findings are reported per document to keep artifacts/reports readable. */
    private static final int MAX_ALT_TEXT_FINDINGS = 50;

    private PdfAccessibilityChecker() {
    }

    /**
     * Scans {@code pdfFile} and writes an interaction-style JSON artifact with any
     * findings. Never throws -- a read/parse failure is logged and reported as a
     * single {@code pdf-parse-error} finding so a broken PDF is visible in reports
     * rather than silently skipped.
     *
     * @param pdfFile path to the PDF to check
     * @param label   human-readable label for reports/artifacts, e.g. the document name
     * @return the findings detected (empty, never {@code null}, when the PDF is clean)
     */
    public static List<AccessibilityFinding> check(Path pdfFile, String label) {
        List<AccessibilityFinding> findings = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile())) {
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDDocumentInformation info = document.getDocumentInformation();

            boolean tagged = isTagged(catalog);
            if (!tagged) {
                findings.add(finding("pdf-tagged", "1.3.1", "serious",
                        "Document is not a Tagged PDF (no MarkInfo/Marked flag and/or no structure tree root); "
                                + "assistive technology cannot reliably determine reading order or element roles.",
                        "(document)", label));
            }

            String title = info != null ? info.getTitle() : null;
            if (title == null || title.trim().isEmpty()) {
                findings.add(finding("pdf-document-title", "2.4.2", "moderate",
                        "Document metadata has no Title set; assistive technology announces the file name instead.",
                        "(document metadata)", label));
            }

            String language = catalog.getLanguage();
            if (language == null || language.trim().isEmpty()) {
                findings.add(finding("pdf-document-language", "3.1.1", "moderate",
                        "Document catalog does not declare a primary language; assistive technology may use "
                                + "incorrect pronunciation rules.",
                        "(document catalog)", label));
            }

            if (tagged) {
                findings.addAll(checkImageAltText(catalog, label));
            }

        } catch (Exception e) {
            logger.warn("PDF accessibility check failed to parse '{}': {}", pdfFile, e.getMessage());
            findings.add(finding("pdf-parse-error", null, "unknown",
                    "Could not parse this PDF for accessibility checks: " + e.getMessage(),
                    "(document)", label));
        }

        writeInteractionArtifact(label, findings);
        return findings;
    }

    private static boolean isTagged(PDDocumentCatalog catalog) {
        PDMarkInfo markInfo = catalog.getMarkInfo();
        PDStructureTreeRoot structRoot = catalog.getStructureTreeRoot();
        return markInfo != null && markInfo.isMarked() && structRoot != null;
    }

    private static List<AccessibilityFinding> checkImageAltText(PDDocumentCatalog catalog, String label) {
        List<AccessibilityFinding> findings = new ArrayList<>();
        PDStructureTreeRoot root = catalog.getStructureTreeRoot();
        if (root == null) {
            return findings;
        }
        walkForMissingAltText(root, findings, label);
        return findings;
    }

    /** Depth-first walk of the structure tree, flagging Figure elements with no alt/actual text. */
    private static void walkForMissingAltText(PDStructureNode node, List<AccessibilityFinding> findings, String label) {
        if (node == null || findings.size() >= MAX_ALT_TEXT_FINDINGS) {
            return;
        }
        List<Object> kids = node.getKids();
        if (kids == null) {
            return;
        }
        for (Object kid : kids) {
            if (findings.size() >= MAX_ALT_TEXT_FINDINGS) {
                return;
            }
            if (!(kid instanceof PDStructureElement)) {
                continue;
            }
            PDStructureElement element = (PDStructureElement) kid;
            String type = element.getStructureType();
            if ("Figure".equals(type)) {
                String alt = element.getAlternateDescription();
                String actualText = element.getActualText();
                boolean hasAlt = alt != null && !alt.trim().isEmpty();
                boolean hasActualText = actualText != null && !actualText.trim().isEmpty();
                if (!hasAlt && !hasActualText) {
                    String identifier = element.getElementIdentifier();
                    findings.add(finding("pdf-image-alt-text", "1.1.1", "serious",
                            "Figure-tagged element has no alternate description or actual-text replacement.",
                            identifier != null && !identifier.trim().isEmpty() ? "Figure[" + identifier + "]" : "Figure",
                            label));
                }
            }
            walkForMissingAltText(element, findings, label);
        }
    }

    private static AccessibilityFinding finding(String ruleId, String wcagCriterion, String severity,
                                                 String description, String element, String pageName) {
        return AccessibilityFinding.builder()
                .ruleId(ruleId)
                .wcagCriterion(wcagCriterion)
                .severity(severity)
                .confidence(AccessibilityFinding.CONFIDENCE_VIOLATION)
                .description(description)
                .affectedElementSelector(element)
                .pageUrl(pageName)
                .engine("PDF")
                .build();
    }

    private static void writeInteractionArtifact(String label, List<AccessibilityFinding> findings) {
        try {
            Path dir = A11yConfig.outputDir();
            Files.createDirectories(dir);
            String ts = LocalDateTime.now().format(TS_FORMAT);
            Path file = dir.resolve(ts + "_" + sanitizeFileName(label) + "_interaction_" + CHECK_ID + ".json");

            ObjectNode root = MAPPER.createObjectNode();
            root.put("timestamp", ts);
            root.put("pageName", label);
            root.put("checkId", CHECK_ID);
            root.put("outcome", findings.isEmpty() ? "PASS" : "FAIL");
            root.put("issueCount", findings.size());

            ArrayNode issues = root.putArray("issues");
            for (AccessibilityFinding f : findings) {
                ObjectNode n = issues.addObject();
                n.put("ruleId", f.getRuleId());
                n.put("description", f.getDescription() != null ? f.getDescription() : "");
                n.put("element", f.getAffectedElementSelector() != null ? f.getAffectedElementSelector() : "");
                n.put("helpUrl", "");
                n.put("needsReview", false);
                n.put("wcagCriterion", f.getWcagCriterion() != null ? f.getWcagCriterion() : "");
                n.put("confidence", f.getConfidence());
            }

            Files.write(file, MAPPER.writeValueAsBytes(root));
        } catch (IOException e) {
            logger.warn("Unable to write PDF accessibility artifact for '{}': {}", label, e.getMessage());
        }
    }

    private static String sanitizeFileName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "unknown";
        }
        String sanitized = raw.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.length() > 80 ? sanitized.substring(0, 80) : sanitized;
    }
}
