package com.test.automation.sdk.accessibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.automation.sdk.accessibility.config.A11yConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates a <b>draft, evidence-based</b> WCAG conformance report in the standard
 * VPAT&reg;-style "Criteria / Conformance Level / Remarks" table format, populated
 * from this run's automated scan artifacts.
 *
 * <h3>What this is -- and is not</h3>
 * <p>This is <b>not</b> an official VPAT (Voluntary Product Accessibility Template) and
 * must never be distributed as one without human review. A real VPAT/Accessibility
 * Conformance Report requires a qualified accessibility professional to verify every
 * criterion, including ones automated tools cannot reach at all (captions, meaningful
 * reading order judgement, error-message quality, etc.). What this generator produces is
 * a starting draft that fills in the standard WCAG 2.1 A/AA criteria table with what our
 * automated evidence actually supports, and is explicit about what it does <b>not</b>
 * know:</p>
 * <ul>
 *   <li><b>Does Not Support</b> -- a criterion this run's engines are known to test,
 *       and at least one counted (non-suppressed) violation was found for it.</li>
 *   <li><b>Supports (No Automated Findings)</b> -- a criterion this run's engines are
 *       known to test, and zero violations were found for it in this run. This is
 *       explicitly <b>not</b> the same as a verified "Supports" in an official VPAT --
 *       it only means the automated checks that exist for this criterion did not fire.</li>
 *   <li><b>Not Evaluated</b> -- a standard WCAG 2.1 A/AA criterion with zero automated
 *       coverage in this SDK (e.g. caption presence, meaningful reading order,
 *       error-message wording quality) -- always requires manual assessment.</li>
 * </ul>
 * <p>"Evaluated" (the first two buckets) is derived empirically: a criterion is only
 * considered covered if at least one violation carrying that {@code wcagCriterion} tag
 * has ever been observed across the raw scan artifacts in this run's internal working
 * directory ({@link A11yConfig#workingDir()}) -- i.e. we only ever claim coverage for
 * criteria our engines are demonstrably capable of flagging, never by static assumption.</p>
 *
 * <p>Like {@link AccessibilitySummaryReportGenerator}, this respects
 * {@link A11ySuppressionRegistry}: a verified false positive is excluded from the
 * "Does Not Support" determination for its criterion (with the exclusion called out in
 * the remarks), consistent with every other report in this suite.</p>
 *
 * <p>Output: {@code <output>/accessibility-vpat-draft.html}. Call once at the end of a
 * run, alongside {@link AccessibilitySummaryReportGenerator#generate()} and
 * {@link AccessibilityExcelReporter#generate()}.</p>
 */
public final class AccessibilityVpatReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AccessibilityVpatReportGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * The standard WCAG 2.1 Level A and AA success criteria (number, name, level).
     * This is fixed reference data (the published WCAG 2.1 criteria list), not
     * derived from any scan -- it is the fixed left-hand column of the report table
     * that every criterion, evaluated or not, must appear in.
     */
    private static final String[][] WCAG_21_A_AA = {
            {"1.1.1", "Non-text Content", "A"},
            {"1.2.1", "Audio-only and Video-only (Prerecorded)", "A"},
            {"1.2.2", "Captions (Prerecorded)", "A"},
            {"1.2.3", "Audio Description or Media Alternative (Prerecorded)", "A"},
            {"1.2.4", "Captions (Live)", "AA"},
            {"1.2.5", "Audio Description (Prerecorded)", "AA"},
            {"1.3.1", "Info and Relationships", "A"},
            {"1.3.2", "Meaningful Sequence", "A"},
            {"1.3.3", "Sensory Characteristics", "A"},
            {"1.3.4", "Orientation", "AA"},
            {"1.3.5", "Identify Input Purpose", "AA"},
            {"1.4.1", "Use of Color", "A"},
            {"1.4.2", "Audio Control", "A"},
            {"1.4.3", "Contrast (Minimum)", "AA"},
            {"1.4.4", "Resize Text", "AA"},
            {"1.4.5", "Images of Text", "AA"},
            {"1.4.10", "Reflow", "AA"},
            {"1.4.11", "Non-text Contrast", "AA"},
            {"1.4.12", "Text Spacing", "AA"},
            {"1.4.13", "Content on Hover or Focus", "AA"},
            {"2.1.1", "Keyboard", "A"},
            {"2.1.2", "No Keyboard Trap", "A"},
            {"2.1.4", "Character Key Shortcuts", "A"},
            {"2.2.1", "Timing Adjustable", "A"},
            {"2.2.2", "Pause, Stop, Hide", "A"},
            {"2.3.1", "Three Flashes or Below Threshold", "A"},
            {"2.4.1", "Bypass Blocks", "A"},
            {"2.4.2", "Page Titled", "A"},
            {"2.4.3", "Focus Order", "A"},
            {"2.4.4", "Link Purpose (In Context)", "A"},
            {"2.4.5", "Multiple Ways", "AA"},
            {"2.4.6", "Headings and Labels", "AA"},
            {"2.4.7", "Focus Visible", "AA"},
            {"2.5.1", "Pointer Gestures", "A"},
            {"2.5.2", "Pointer Cancellation", "A"},
            {"2.5.3", "Label in Name", "A"},
            {"2.5.4", "Motion Actuation", "A"},
            {"3.1.1", "Language of Page", "A"},
            {"3.1.2", "Language of Parts", "AA"},
            {"3.2.1", "On Focus", "A"},
            {"3.2.2", "On Input", "A"},
            {"3.2.3", "Consistent Navigation", "AA"},
            {"3.2.4", "Consistent Identification", "AA"},
            {"3.3.1", "Error Identification", "A"},
            {"3.3.2", "Labels or Instructions", "A"},
            {"3.3.3", "Error Suggestion", "AA"},
            {"3.3.4", "Error Prevention (Legal, Financial, Data)", "AA"},
            {"4.1.1", "Parsing", "A"},
            {"4.1.2", "Name, Role, Value", "A"},
            {"4.1.3", "Status Messages", "AA"},
    };

    /** Published report directory (fresh on every call) — the final VPAT draft is written here. */
    private static Path reportDir() {
        return A11yConfig.outputDir();
    }

    /** Internal working directory (fresh on every call) — raw scan artifacts are read from here. */
    private static Path workingDir() {
        return A11yConfig.workingDir();
    }

    private AccessibilityVpatReportGenerator() {
    }

    public static Path generate() {
        Path dir = reportDir();
        Path report = dir.resolve("accessibility-vpat-draft.html");
        try {
            Files.createDirectories(dir);
            List<Finding> findings = loadFindings();
            String html = buildHtml(findings);
            Files.write(report, html.getBytes(StandardCharsets.UTF_8));
            logger.info("Accessibility VPAT draft report generated: {}", report.toAbsolutePath());
            return report;
        } catch (Exception e) {
            logger.warn("Unable to generate accessibility VPAT draft report: {}", e.getMessage(), e);
            return null;
        }
    }

    /** One violation instance, reduced to only what the VPAT mapping needs. */
    private static final class Finding {
        String ruleId;
        String wcagCriterion;
        String pageName;
        boolean suppressed;
    }

    private static List<Finding> loadFindings() throws IOException {
        List<Finding> findings = new ArrayList<>();
        if (!Files.exists(workingDir())) {
            return findings;
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(workingDir())) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith("_a11y.json") || name.contains("_interaction_");
                    })
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());
        }

        for (Path file : files) {
            try {
                JsonNode root = MAPPER.readTree(file.toFile());
                String pageName = root.path("pageName").asText("(unknown)");
                boolean isInteraction = file.getFileName().toString().contains("_interaction_");
                JsonNode issues = isInteraction ? root.path("issues") : root.path("violations");
                for (JsonNode issue : issues) {
                    Finding f = new Finding();
                    f.ruleId = isInteraction ? issue.path("ruleId").asText("") : issue.path("id").asText("");
                    f.wcagCriterion = issue.path("wcagCriterion").asText("");
                    f.pageName = pageName;
                    if (f.wcagCriterion.trim().isEmpty()) {
                        // No SC mapping available for this rule -- cannot attribute it to
                        // any criterion row, so it is excluded from the VPAT table (it is
                        // still fully visible in the HTML/Excel summary reports).
                        continue;
                    }
                    f.suppressed = A11ySuppressionRegistry.isActivelySuppressed(f.ruleId);
                    findings.add(f);
                }
            } catch (Exception ex) {
                logger.debug("Skipping malformed accessibility artifact {}: {}", file, ex.getMessage());
            }
        }
        return findings;
    }

    private static String buildHtml(List<Finding> findings) {
        // Criterion -> counted (non-suppressed) findings, and criterion -> suppressed count.
        Map<String, List<Finding>> countedByCriterion = new LinkedHashMap<>();
        Map<String, Integer> suppressedCountByCriterion = new LinkedHashMap<>();
        for (Finding f : findings) {
            if (f.suppressed) {
                suppressedCountByCriterion.merge(f.wcagCriterion, 1, Integer::sum);
            } else {
                countedByCriterion.computeIfAbsent(f.wcagCriterion, k -> new ArrayList<>()).add(f);
            }
        }
        // "Evaluated" = every criterion our engines are demonstrably capable of flagging,
        // i.e. it appeared on at least one finding (counted or suppressed) in this run.
        Set<String> evaluatedCriteria = new LinkedHashSet<>();
        evaluatedCriteria.addAll(countedByCriterion.keySet());
        evaluatedCriteria.addAll(suppressedCountByCriterion.keySet());

        String generatedAt = LocalDateTime.now().format(DISPLAY_TS);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
            .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>Accessibility Conformance Report -- DRAFT</title><style>")
            .append("*{box-sizing:border-box;margin:0;padding:0;}")
            .append("body{font-family:'Segoe UI',Arial,sans-serif;background:#F4F4F4;color:#1A1A1A;font-size:14px;}")
            .append(".content{max-width:1280px;margin:0 auto;padding:24px;}")
            .append("h1{font-size:22px;margin-bottom:4px;}")
            .append(".meta{font-size:12px;color:#555;margin-bottom:16px;}")
            .append(".disclaimer{background:#FFF3CD;border-left:5px solid #DC3545;border-radius:0 6px 6px 0;"
                    + "padding:14px 18px;margin-bottom:24px;font-size:13px;line-height:1.7;}")
            .append(".disclaimer b.title{display:block;font-size:15px;margin-bottom:6px;}")
            .append("table{width:100%;border-collapse:collapse;background:#FFF;margin-bottom:28px;"
                    + "box-shadow:0 1px 4px rgba(0,0,0,.08);}")
            .append("th,td{padding:8px 10px;border-bottom:1px solid #EEE;text-align:left;font-size:12.5px;"
                    + "vertical-align:top;}")
            .append("th{background:#1A1A1A;color:#FFF;text-transform:uppercase;font-size:11px;letter-spacing:.4px;}")
            .append(".lvl-supports{color:#28A745;font-weight:700;}")
            .append(".lvl-nosupport{color:#D4006E;font-weight:700;}")
            .append(".lvl-notevaluated{color:#888;font-weight:700;}")
            .append(".section-head{background:#1A1A1A;color:#FFF;padding:8px 14px;font-size:13px;font-weight:700;"
                    + "text-transform:uppercase;letter-spacing:.5px;margin:20px 0 0;}")
            .append("</style></head><body><div class='content'>")
            .append("<h1>Accessibility Conformance Report (WCAG 2.1 A/AA) -- DRAFT</h1>")
            .append("<div class='meta'>Generated: ").append(escapeHtml(generatedAt))
            .append(" &nbsp;|&nbsp; SDK v").append(escapeHtml(A11yLibraryVersion.get()))
            .append(" &nbsp;|&nbsp; Source: ").append(escapeHtml(workingDir().toString())).append("</div>");

        html.append("<div class='disclaimer'>")
            .append("<b class='title'>&#9888; DRAFT -- Automated Evidence Only, Not an Official VPAT&reg;</b>")
            .append("This report is <b>not</b> an official VPAT / Accessibility Conformance Report and must not be ")
            .append("distributed externally as one. It was populated entirely from this run's automated scan ")
            .append("evidence and requires review, correction, and sign-off by a qualified accessibility ")
            .append("professional before use. Automated tools (this SDK included) typically detect only ")
            .append("roughly 30&ndash;40% of real WCAG conformance issues -- a criterion marked ")
            .append("<b>Supports</b> below means only that <i>no automated finding fired for it in this run</i>, ")
            .append("not that it has been manually verified. Criteria marked <b>Not Evaluated</b> have zero ")
            .append("automated coverage in this SDK and always require manual assessment (e.g. caption presence, ")
            .append("meaningful reading order, or error-message wording quality).")
            .append("</div>");

        html.append("<table><thead><tr><th>Criteria</th><th>Conformance Level</th>"
                + "<th>Remarks and Explanations</th></tr></thead><tbody>");

        for (String[] sc : WCAG_21_A_AA) {
            String number = sc[0];
            String name = sc[1];
            String level = sc[2];
            html.append("<tr><td><b>").append(number).append("</b> ").append(escapeHtml(name))
                .append(" <span style='color:#888;'>(Level ").append(level).append(")</span></td>");

            if (!evaluatedCriteria.contains(number)) {
                html.append("<td class='lvl-notevaluated'>Not Evaluated</td>")
                    .append("<td>Not covered by this SDK's automated checks in this run -- requires manual assessment.</td>");
            } else {
                List<Finding> counted = countedByCriterion.getOrDefault(number, java.util.Collections.emptyList());
                int suppressedCount = suppressedCountByCriterion.getOrDefault(number, 0);
                if (counted.isEmpty()) {
                    html.append("<td class='lvl-supports'>Supports (No Automated Findings)</td><td>")
                        .append("No counted automated violations mapped to this criterion in this run.");
                    if (suppressedCount > 0) {
                        html.append(" (").append(suppressedCount)
                            .append(" verified false positive(s) excluded -- see Verified False Positives section.)");
                    }
                    html.append("</td>");
                } else {
                    Set<String> ruleIds = counted.stream().map(f -> f.ruleId).collect(Collectors.toCollection(LinkedHashSet::new));
                    Set<String> pages = counted.stream().map(f -> f.pageName).collect(Collectors.toCollection(LinkedHashSet::new));
                    html.append("<td class='lvl-nosupport'>Does Not Support</td><td>")
                        .append(counted.size()).append(" automated finding(s) across ").append(pages.size())
                        .append(" page(s). Rule(s): <code>").append(escapeHtml(String.join(", ", ruleIds))).append("</code>");
                    if (suppressedCount > 0) {
                        html.append(" (").append(suppressedCount)
                            .append(" additional verified false positive(s) excluded from this count.)");
                    }
                    html.append("</td>");
                }
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");

        html.append("</div></body></html>");
        return html.toString();
    }

    private static String escapeHtml(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
