package com.test.automation.sdk.accessibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.automation.sdk.accessibility.config.A11yConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a leadership-friendly HTML summary from the per-scan {@code _a11y.json}
 * artifacts produced by {@link AccessibilityChecker}.
 *
 * <p>Output: {@code <output>/accessibility-summary.html}. Call once at the end of a
 * run (e.g. from a JUnit {@code @AfterAll} or TestNG {@code @AfterSuite}).</p>
 */
public final class AccessibilitySummaryReportGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AccessibilitySummaryReportGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Published report directory (fresh on every call) — the final HTML is written here. */
    private static Path reportDir() { return A11yConfig.outputDir(); }

    /** Internal working directory (fresh on every call) — raw scan artifacts are read from here. */
    private static Path workingDir() { return A11yConfig.workingDir(); }

    private AccessibilitySummaryReportGenerator() {
    }

    public static Path generate() {
        Path dir    = reportDir();
        Path report = dir.resolve("accessibility-summary.html");
        try {
            Files.createDirectories(dir);
            List<ScanRecord> scans = loadScanRecords();
            String html = buildHtml(scans);
            Files.write(report, html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            logger.info("Accessibility HTML summary generated: {}", report.toAbsolutePath());
            return report;
        } catch (Exception e) {
            logger.warn("Unable to generate accessibility summary HTML: {}", e.getMessage(), e);
            return null;
        }
    }

    private static List<ScanRecord> loadScanRecords() throws IOException {
        if (!Files.exists(workingDir())) {
            return Collections.emptyList();
        }

        // Collect both axe-core scan artifacts (*_a11y.json) AND interaction/structural/
        // WCAG-2.2/motion artifacts (*_interaction_*.json) so every engine layer is
        // represented in the HTML report. Previously only _a11y.json files were read
        // here, which silently dropped all Layers 2-5 findings from this report even
        // though AccessibilityExcelReporter already included them — this closes that
        // data-loss gap and brings the HTML report to parity with the Excel report.
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

        List<ScanRecord> records = new ArrayList<>();
        for (Path file : files) {
            try {
                String fileName = file.getFileName().toString();
                ScanRecord record = fileName.contains("_interaction_")
                        ? parseInteractionArtifact(file)
                        : parseAxeArtifact(file);
                if (record != null) {
                    applySuppression(record);
                    records.add(record);
                }
            } catch (Exception ex) {
                logger.debug("Skipping malformed accessibility artifact {}: {}", file, ex.getMessage());
            }
        }
        return records;
    }

    private static ScanRecord parseAxeArtifact(Path file) throws IOException {
        JsonNode root = MAPPER.readTree(file.toFile());
        ScanRecord record = new ScanRecord();
        record.timestamp = root.path("timestamp").asText("");
        record.pageName = root.path("pageName").asText("(unknown)");
        record.outcome = root.path("outcome").asText("UNKNOWN");
        record.violationCount = root.path("violationCount").asInt(0);
        record.engine = "axe-core";

        for (JsonNode v : root.path("violations")) {
            String id = v.path("id").asText("");
            String impact = v.path("impact").asText("UNKNOWN").toUpperCase(Locale.ROOT);
            if (!id.trim().isEmpty()) {
                record.ruleIds.add(id);
            }
            record.impacts.add(impact);

            ViolationDetail vd = new ViolationDetail();
            vd.ruleId      = id;
            vd.impact      = impact;
            vd.findingType = "VIOLATION";
            vd.description = v.path("description").asText("");
            vd.help        = v.path("help").asText("");
            vd.helpUrl     = v.path("helpUrl").asText("");
            vd.engine      = record.engine;
            for (JsonNode el : v.path("affectedElements")) {
                String elText = el.asText("").trim();
                if (!elText.trim().isEmpty()) vd.affectedElements.add(elText);
            }
            record.violations.add(vd);
        }

        // axe-core "incomplete" rules — could not be automatically confirmed pass/fail
        // and require human review. Classified as findingType=NEEDS_REVIEW, never merged
        // into "violations"/violationCount, so PASS/FAIL and enforcement are unaffected.
        // Impact still comes from the rule's own metadata, independent of finding type.
        record.needsReviewCount = 0;
        for (JsonNode v : root.path("incomplete")) {
            String id = v.path("id").asText("");
            String impact = v.path("impact").asText("UNKNOWN").toUpperCase(Locale.ROOT);

            ViolationDetail vd = new ViolationDetail();
            vd.ruleId      = id;
            vd.impact      = impact;
            vd.findingType = "NEEDS_REVIEW";
            vd.description = v.path("description").asText("");
            vd.help        = v.path("help").asText("");
            vd.helpUrl     = v.path("helpUrl").asText("");
            vd.engine      = record.engine;
            for (JsonNode el : v.path("affectedElements")) {
                String elText = el.asText("").trim();
                if (!elText.trim().isEmpty()) vd.affectedElements.add(elText);
            }
            record.violations.add(vd);
            record.needsReviewCount++;
        }
        return record;
    }

    /**
     * Parses a Layer 2-5 interaction/structural/WCAG2.2/motion artifact
     * ({@code *_interaction_<checkId>.json}) written by
     * {@link AccessibilityChecker#writeInteractionArtifact}. Mirrors
     * {@code AccessibilityExcelReporter.parseInteractionArtifact} so both reports agree
     * on engine tagging and needs-review handling.
     */
    private static ScanRecord parseInteractionArtifact(Path file) throws IOException {
        JsonNode root = MAPPER.readTree(file.toFile());
        ScanRecord record = new ScanRecord();
        record.timestamp      = root.path("timestamp").asText("");
        record.pageName       = root.path("pageName").asText("(unknown)");
        record.outcome        = root.path("outcome").asText("UNKNOWN");
        record.engine         = "Interaction:" + root.path("checkId").asText("custom");
        record.violationCount = root.path("issueCount").asInt(0);

        for (JsonNode issue : root.path("issues")) {
            String id = issue.path("ruleId").asText("");
            // Impact is a severity value and must remain independent of finding type —
            // "needsReview" is modeled below as its own findingType dimension, never
            // overwritten into impact (previously this method set impact="NEEDS_REVIEW",
            // which corrupted severity-based grouping/filtering and orphaned these
            // findings from the Impact Distribution / Violations Drilldown sections).
            String impact = issue.path("impact").asText("MODERATE").toUpperCase(Locale.ROOT);
            boolean needsReview = issue.path("needsReview").asBoolean(false);
            if (!id.trim().isEmpty()) {
                record.ruleIds.add(id);
            }
            record.impacts.add(impact);
            if (needsReview) {
                record.needsReviewCount++;
            }

            ViolationDetail vd = new ViolationDetail();
            vd.ruleId      = id;
            vd.impact      = impact;
            vd.findingType = needsReview ? "NEEDS_REVIEW" : "VIOLATION";
            vd.description = issue.path("description").asText("");
            vd.help        = issue.path("wcagRef").asText("");
            vd.helpUrl     = issue.path("helpUrl").asText("");
            vd.engine      = record.engine;
            String element = issue.path("element").asText("").trim();
            if (!element.isEmpty()) vd.affectedElements.add(element);
            record.violations.add(vd);
        }
        return record;
    }

    /**
     * Moves actively-suppressed (verified false positive) violations out of the
     * counted totals for this scan and into {@code record.suppressedViolations}.
     * <p>
     * This is applied once, at report-generation time, right after a {@code ScanRecord}
     * is parsed from its {@code _a11y.json} artifact -- it has ZERO effect on live scan
     * behavior in {@link AccessibilityChecker} (which already ran and wrote that artifact
     * before this method is ever called). Suppressed violations are never dropped from the
     * report entirely; they remain fully visible in the dedicated "Verified False
     * Positives" section, only excluded from the counted totals/PASS-FAIL outcome.
     */
    private static void applySuppression(ScanRecord record) {
        if (record.violations.isEmpty()) {
            return;
        }
        List<ViolationDetail> counted = new ArrayList<>();
        for (ViolationDetail vd : record.violations) {
            if (A11ySuppressionRegistry.isActivelySuppressed(vd.ruleId)) {
                record.suppressedViolations.add(vd);
            } else {
                counted.add(vd);
            }
        }
        if (record.suppressedViolations.isEmpty()) {
            return;
        }
        record.violations.clear();
        record.violations.addAll(counted);
        record.violationCount = counted.size();
        record.ruleIds.clear();
        record.impacts.clear();
        for (ViolationDetail vd : counted) {
            if (!vd.ruleId.trim().isEmpty()) {
                record.ruleIds.add(vd.ruleId);
            }
            record.impacts.add(vd.impact);
        }
        record.needsReviewCount = (int) counted.stream()
                .filter(vd -> "NEEDS_REVIEW".equals(vd.findingType)).count();
        // Only downgrade a FAIL to PASS once every violation on this scan was suppressed --
        // never touch SKIPPED/ERROR outcomes, which are unrelated to violation counting.
        if (counted.isEmpty() && "FAIL".equalsIgnoreCase(record.outcome)) {
            record.outcome = "PASS";
        }
    }

    private static String buildHtml(List<ScanRecord> scans) {
        // ── Read noise config for the informational banner only ───────────
        // NOTE: noise filtering is intentionally NOT applied to report data.
        // The HTML report shows the same complete, unfiltered violation data
        // as the Excel report. Config values below are displayed as a
        // transparency note so readers know what settings are active.
        String thresholdRaw = A11yConfig.get("accessibility.session.noise.threshold", "MINOR");
        A11ySessionManager.ImpactThreshold threshold =
                A11ySessionManager.ImpactThreshold.from(thresholdRaw);

        Set<String> allowedRules = new HashSet<>();
        String allowedRaw = A11yConfig.get("accessibility.session.allowed.rules", "");
        if (allowedRaw != null && !allowedRaw.trim().isEmpty()) {
            for (String r : allowedRaw.split(",")) {
                String trimmed = r.trim();
                if (!trimmed.isEmpty()) allowedRules.add(trimmed);
            }
        }

        int totalScans = scans.size();
        long pass    = scans.stream().filter(s -> "PASS".equalsIgnoreCase(s.outcome)).count();
        long fail    = scans.stream().filter(s -> "FAIL".equalsIgnoreCase(s.outcome)).count();
        long skipped = scans.stream().filter(s -> "SKIPPED".equalsIgnoreCase(s.outcome)).count();
        long error   = scans.stream().filter(s -> "ERROR".equalsIgnoreCase(s.outcome)).count();
        // "Violations" and "Needs Review" are independent findingType-based counts, derived
        // directly from each ViolationDetail's findingType — never from impact (impact stays a
        // pure severity value) and never from ScanRecord.violationCount (which is a per-scan
        // "total issues detected" figure used only for the Recent Scans/Scan Details tables).
        long totalViolations = scans.stream().flatMap(s -> s.violations.stream())
                .filter(v -> !"NEEDS_REVIEW".equals(v.findingType)).count();
        long totalNeedsReview = scans.stream().flatMap(s -> s.violations.stream())
                .filter(v -> "NEEDS_REVIEW".equals(v.findingType)).count();

        Map<String, Integer> impactCounts = new HashMap<>();
        Map<String, Integer> ruleCounts   = new HashMap<>();
        Map<String, Integer> pageViolationCounts = new HashMap<>();

        for (ScanRecord scan : scans) {
            pageViolationCounts.merge(scan.pageName, scan.violationCount, Integer::sum);
            for (String impact  : scan.impacts)  { impactCounts.merge(impact, 1, Integer::sum); }
            for (String ruleId  : scan.ruleIds)  { ruleCounts.merge(ruleId, 1, Integer::sum); }
        }

        // ── Cross-page dedup: group violations by ruleId → pages affected ───
        Map<String, Set<String>> ruleToPages = new HashMap<>();
        for (ScanRecord scan : scans) {
            for (String ruleId : scan.ruleIds) {
                ruleToPages.computeIfAbsent(ruleId, k -> new HashSet<>()).add(scan.pageName);
            }
        }
        // sort by pages-affected descending, then rule ID ascending (explicit lambda for Java 11)
        List<Map.Entry<String, Set<String>>> dedupRules = ruleToPages.entrySet().stream()
                .sorted((a, b) -> {
                    int diff = b.getValue().size() - a.getValue().size();
                    return diff != 0 ? diff : a.getKey().compareTo(b.getKey());
                })
                .limit(15)
                .collect(Collectors.toList());

        List<Map.Entry<String, Integer>> topRules = ruleCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10).collect(Collectors.toList());

        List<Map.Entry<String, Integer>> topPages = pageViolationCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10).collect(Collectors.toList());

        String generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String projectName = resolveProjectName();
        boolean noiseActive = threshold != A11ySessionManager.ImpactThreshold.MINOR
                || !allowedRules.isEmpty();

        StringBuilder html = new StringBuilder();

        // ── OTI-branded document head ────────────────────────────────────────
        html.append("<!DOCTYPE html>")
            .append("<html lang='en'>")
            .append("<head>")
            .append("<meta charset='UTF-8'>")
            .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>NYC OTI — Accessibility Report — ").append(escapeHtml(projectName)).append("</title>")
            .append("<style>")

            // Reset & base
            .append("*{box-sizing:border-box;margin:0;padding:0;}")
            .append("body{font-family:'Segoe UI',Arial,sans-serif;background:#F4F4F4;color:#1A1A1A;font-size:14px;}")
            .append("a{color:#D4006E;text-decoration:none;}")
            .append("a:hover{text-decoration:underline;}")

            // Top utility bar (black with white text — like the NYC.gov bar)
            .append(".top-bar{background:#1A1A1A;color:#FFF;padding:5px 24px;"
                    + "font-size:11px;display:flex;align-items:center;gap:10px;letter-spacing:.3px;}")
            .append(".top-bar .nyc-wordmark{font-weight:900;font-size:13px;letter-spacing:1px;}")
            .append(".top-bar .divider{width:1px;height:14px;background:#555;}")
            .append(".top-bar .agency{color:#CCC;}")

            // Main header (white with pink bottom border)
            .append(".site-header{background:#FFF;border-bottom:3px solid #D4006E;"
                    + "padding:12px 24px;display:flex;align-items:center;justify-content:space-between;}")
            .append(".oti-logo{display:flex;align-items:baseline;gap:0;line-height:1;}")
            .append(".oti-logo .nyc{font-size:32px;font-weight:900;color:#1A1A1A;letter-spacing:-1px;}")
            .append(".oti-logo .oti{font-size:32px;font-weight:900;color:#F7901D;letter-spacing:-1px;}")
            .append(".header-right{text-align:right;}")
            .append(".header-right .report-label{font-size:11px;text-transform:uppercase;"
                    + "letter-spacing:1px;color:#888;font-weight:600;}")
            .append(".header-right .report-name{font-size:15px;font-weight:700;color:#1A1A1A;}")
            .append(".header-right .report-project{font-size:12px;font-weight:600;color:#D4006E;margin-top:2px;}")

            // Hero / page-title band (pink → orange gradient with subtle circuit hint)
            .append(".hero{background:linear-gradient(120deg,#D4006E 0%,#E8196E 45%,#F7901D 100%);"
                    + "color:#FFF;padding:28px 24px 24px;position:relative;overflow:hidden;}")
            .append(".hero::after{content:'';position:absolute;top:0;right:0;bottom:0;width:220px;"
                    + "opacity:.07;background-image:radial-gradient(circle,#FFF 1px,transparent 1px),"
                    + "radial-gradient(circle,#FFF 1px,transparent 1px);"
                    + "background-size:18px 18px;background-position:0 0,9px 9px;}")
            .append(".hero h1{font-size:24px;font-weight:800;letter-spacing:-.5px;margin-bottom:4px;}")
            .append(".hero .project-name{font-size:16px;font-weight:700;letter-spacing:.3px;margin-bottom:6px;"
                    + "display:inline-block;background:rgba(255,255,255,.18);padding:3px 12px;border-radius:4px;}")
            .append(".hero .meta{font-size:12px;opacity:.85;margin-top:2px;}")
            .append(".hero .meta span{margin-right:20px;}")

            // Content wrapper
            .append(".content{max-width:1280px;margin:0 auto;padding:24px;}")

            // Noise / suppression banner
            .append(".noise-banner{background:#FFF3CD;border-left:4px solid #F7901D;"
                    + "border-radius:0 6px 6px 0;padding:10px 16px;"
                    + "margin-bottom:20px;font-size:13px;line-height:1.6;}")

            // KPI card grid
            .append(".kpi-grid{display:flex;flex-wrap:wrap;gap:14px;margin-bottom:28px;}")
            .append(".kpi-card{background:#FFF;flex:1;min-width:120px;"
                    + "border-top:4px solid #D4006E;box-shadow:0 1px 4px rgba(0,0,0,.08);"
                    + "padding:14px 18px;}")
            .append(".kpi-card.c-pass{border-top-color:#28A745;}")
            .append(".kpi-card.c-fail{border-top-color:#D4006E;}")
            .append(".kpi-card.c-skip{border-top-color:#F7901D;}")
            .append(".kpi-card.c-error{border-top-color:#DC3545;}")
            .append(".kpi-card.c-info{border-top-color:#1A1A1A;}")
            .append(".kpi-card .kpi-label{font-size:10px;text-transform:uppercase;"
                    + "letter-spacing:.8px;color:#888;font-weight:600;margin-bottom:6px;}")
            .append(".kpi-card .kpi-value{font-size:30px;font-weight:800;color:#1A1A1A;line-height:1;}")
            .append(".kpi-card.c-pass .kpi-value{color:#28A745;}")
            .append(".kpi-card.c-fail .kpi-value{color:#D4006E;}")
            .append(".kpi-card.c-skip .kpi-value{color:#F7901D;}")
            .append(".kpi-card.c-error .kpi-value{color:#DC3545;}")

            // Section headings
            .append(".section{margin-bottom:28px;}")
            .append(".section-head{background:#1A1A1A;color:#FFF;"
                    + "padding:10px 16px;border-left:5px solid #D4006E;"
                    + "font-size:14px;font-weight:700;text-transform:uppercase;"
                    + "letter-spacing:.6px;display:flex;align-items:center;gap:10px;}")
            .append(".section-subtext{font-size:12px;color:#666;padding:8px 0 10px;}")

            // Tables
            .append("table{border-collapse:collapse;width:100%;background:#FFF;"
                    + "box-shadow:0 1px 4px rgba(0,0,0,.06);}")
            .append("thead tr{background:#F9F9F9;}")
            .append("th{padding:10px 14px;text-align:left;font-size:11px;"
                    + "text-transform:uppercase;letter-spacing:.5px;font-weight:700;"
                    + "color:#555;border-bottom:2px solid #D4006E;white-space:nowrap;}")
            .append("td{padding:10px 14px;font-size:13px;"
                    + "border-bottom:1px solid #EFEFEF;vertical-align:middle;}")
            .append("tbody tr:hover td{background:#FEF0F7;}")
            .append("tbody tr:last-child td{border-bottom:none;}")

            // Impact & outcome pills
            .append(".pill{display:inline-block;border-radius:3px;padding:2px 8px;"
                    + "font-size:11px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;}")
            .append(".pill-critical{background:#FDECEA;color:#B71C1C;}")
            .append(".pill-serious{background:#FEF3E2;color:#E65100;}")
            .append(".pill-moderate{background:#FFF8E1;color:#F57F17;}")
            .append(".pill-minor{background:#E8F5E9;color:#2E7D32;}")
            .append(".pill-pass{background:#E8F5E9;color:#2E7D32;}")
            .append(".pill-fail{background:#FDECEA;color:#D4006E;}")
            .append(".pill-skipped{background:#FEF3E2;color:#E65100;}")
            .append(".pill-error{background:#F3E5F5;color:#6A1B9A;}")
            .append(".pill-unknown{background:#EEEEEE;color:#555;}")
            .append(".pill-needs_review{background:#E3F2FD;color:#1565C0;}")

            // Wide-cell truncation
            .append(".truncate{max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}")

            // Footer
            .append(".site-footer{background:#1A1A1A;color:#FFF;"
                    + "padding:20px 24px;margin-top:36px;display:flex;"
                    + "align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;}")
            .append(".footer-logo .nyc{font-size:20px;font-weight:900;color:#FFF;letter-spacing:-1px;}")
            .append(".footer-logo .oti{font-size:20px;font-weight:900;color:#F7901D;letter-spacing:-1px;}")
            .append(".footer-meta{font-size:11px;color:#AAA;}")

            // ── Expandable details/summary sections ─────────────────────────────
            .append("details.section>summary{cursor:pointer;user-select:none;list-style:none;"
                    + "display:flex;align-items:center;gap:8px;}")
            .append("details.section>summary::-webkit-details-marker{display:none;}")
            .append("details.section>summary::marker{display:none;}")
            .append(".toggle-btns{display:flex;gap:8px;padding:10px 0 12px;}")
            .append(".toggle-btn{background:#F4F4F4;border:1px solid #DDD;border-radius:4px;"
                    + "padding:4px 12px;cursor:pointer;font-size:11px;color:#555;font-weight:600;}")
            .append(".toggle-btn:hover{background:#E8E8E8;border-color:#BBB;}")
            // impact groups
            .append(".impact-group{margin:3px 0;border-radius:4px;overflow:hidden;}")
            .append(".impact-group>summary{cursor:pointer;list-style:none;padding:9px 14px;"
                    + "display:flex;align-items:center;gap:10px;user-select:none;"
                    + "font-weight:700;font-size:13px;}")
            .append(".impact-group>summary::-webkit-details-marker{display:none;}")
            .append(".impact-group>summary::marker{display:none;}")
            .append(".ig-critical>summary{background:#FDECEA;border-left:4px solid #B71C1C;}")
            .append(".ig-serious>summary{background:#FEF3E2;border-left:4px solid #E65100;}")
            .append(".ig-moderate>summary{background:#FFF8E1;border-left:4px solid #F57F17;}")
            .append(".ig-minor>summary{background:#E8F5E9;border-left:4px solid #2E7D32;}")
            .append(".ig-unknown>summary{background:#EEEEEE;border-left:4px solid #888;}")
            .append(".ig-pad{padding:6px 10px 10px;}")
            // violation card
            .append(".v-card{margin:3px 0;border:1px solid #EEE;border-radius:4px;background:#FFF;overflow:hidden;}")
            .append(".v-card>summary{cursor:pointer;list-style:none;padding:8px 12px;"
                    + "display:flex;align-items:baseline;gap:8px;background:#FAFAFA;"
                    + "user-select:none;font-size:12px;flex-wrap:wrap;line-height:1.5;}")
            .append(".v-card>summary::-webkit-details-marker{display:none;}")
            .append(".v-card>summary::marker{display:none;}")
            .append(".v-card>summary:hover{background:#F3F3F3;}")
            .append(".v-card-body{padding:10px 14px;font-size:12px;border-top:1px solid #EEE;line-height:1.8;}")
            .append(".v-label{font-weight:700;color:#555;min-width:100px;display:inline-block;}")
            .append(".el-code{font-family:'Consolas','Courier New',monospace;font-size:10.5px;"
                    + "background:#F5F5F5;padding:3px 7px;border-radius:3px;margin:2px 0;"
                    + "display:block;overflow-x:auto;max-height:72px;white-space:pre-wrap;"
                    + "word-break:break-all;border-left:3px solid #DDD;color:#333;}")
            // scan card
            .append(".scan-card{margin:4px 0;border:1px solid #E8E8E8;border-radius:4px;background:#FFF;overflow:hidden;}")
            .append(".scan-card>summary{cursor:pointer;list-style:none;padding:10px 14px;"
                    + "display:flex;align-items:center;gap:10px;flex-wrap:wrap;"
                    + "background:#FAFAFA;user-select:none;font-size:13px;}")
            .append(".scan-card>summary::-webkit-details-marker{display:none;}")
            .append(".scan-card>summary::marker{display:none;}")
            .append(".scan-card>summary:hover{background:#F0F0F0;}")
            // search / all-issues
            .append(".search-wrap{padding:10px 0 12px;display:flex;align-items:center;gap:12px;flex-wrap:wrap;}")
            .append(".search-input{flex:1;min-width:220px;padding:7px 11px;border:1px solid #CCC;"
                    + "border-radius:4px;font-size:13px;outline:none;}")
            .append(".search-input:focus{border-color:#D4006E;box-shadow:0 0 0 2px rgba(212,0,110,.12);}")
            .append(".result-count{font-size:12px;color:#888;white-space:nowrap;}")
            // engine filter chips
            .append(".engine-filter{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:4px 0 12px;}")
            .append(".engine-filter .filter-label{font-size:12px;font-weight:700;color:#555;white-space:nowrap;}")
            .append(".eng-chip{display:inline-flex;align-items:center;gap:5px;cursor:pointer;"
                    + "border:1px solid #CCC;border-radius:20px;padding:4px 12px;font-size:12px;"
                    + "background:#F9F9F9;color:#555;user-select:none;transition:background .15s,border-color .15s,color .15s;}")
            .append(".eng-chip:hover{background:#F0F0F0;border-color:#AAA;}")
            .append(".eng-chip input[type=checkbox]{width:13px;height:13px;accent-color:#D4006E;cursor:pointer;}")

            .append("</style></head><body>");

        // ── Top utility bar ──────────────────────────────────────────────────
        html.append("<div class='top-bar'>")
            .append("<span class='nyc-wordmark'>NYC</span>")
            .append("<span class='divider'></span>")
            .append("<span class='agency'>Office of Technology &amp; Innovation</span>")
            .append("</div>");

        // ── Site header ──────────────────────────────────────────────────────
        html.append("<header class='site-header'>")
            .append("<div class='oti-logo'>")
            .append("<span class='nyc'>NYC</span><span class='oti'>OTI</span>")
            .append("</div>")
            .append("<div class='header-right'>")
            .append("<div class='report-label'>Automated Testing</div>")
            .append("<div class='report-name'>Accessibility Summary Report</div>")
            .append("<div class='report-project'>&#128193; ").append(escapeHtml(projectName)).append("</div>")
            .append("<div class='report-project' style='color:#888;font-weight:400;'>SDK v")
            .append(escapeHtml(A11yLibraryVersion.get())).append("</div>")
            .append("</div>")
            .append("</header>");

        // ── Hero ─────────────────────────────────────────────────────────────
        html.append("<div class='hero'>")
            .append("<h1>Accessibility Summary</h1>")
            .append("<div class='project-name'>").append(escapeHtml(projectName)).append("</div>")
            .append("<div class='meta'>")
            .append("<span>&#128197; Generated: ").append(escapeHtml(generatedAt)).append("</span>")
            .append("<span>&#128194; Source: ").append(escapeHtml(workingDir().toString())).append("</span>")
            .append("</div>")
            .append("</div>");

        // ── Global engine filter — sits between hero and all sections ────────
        // Report-only filter: recomputes what is DISPLAYED (KPI cards, tables, and
        // per-violation sections) purely client-side; never touches the underlying
        // scan artifacts, suppression state, or pass/fail enforcement outcome. Chip
        // values are prefix-matched against ScanRecord/ViolationDetail.engine, so
        // checking "Interaction" also shows every "Interaction:<checkId>" (Layers
        // 2-5: interaction/WCAG2.2/structural/motion) finding. Deliberately 2 chips
        // rather than the reference implementation's 3 (axe-core / Interaction /
        // NeedsReview) — this SDK exposes "needs review" as its own independent
        // Finding Type Filter dimension (below) instead of a 3rd Engine chip, so the
        // same fact is never represented in two different filter dimensions at once.
        html.append("<div class='engine-filter' id='a11yEngineFilter' "
                + "style='margin:0;border-radius:0;border-left:none;border-right:none;"
                + "padding:10px 24px;background:#FFF8FB;border-top:1px solid #EEE;border-bottom:1px solid #EEE;'>")
            .append("<span class='filter-label'>&#9881; Engine Filter &nbsp;"
                + "<span style='font-size:11px;font-weight:400;color:#888;'>"
                + "(applies to all sections below)</span>:</span>");
        for (String eng : new String[]{"axe-core", "Interaction"}) {
            html.append("<label class='eng-chip' style='background:#FEF0F7;border-color:#D4006E;"
                    + "color:#D4006E;font-weight:700;'>")
                .append("<input type='checkbox' class='eng-chk' value='")
                .append(escapeHtml(eng)).append("' checked ")
                .append("onchange=\"a11yEngineToggle(this)\">")
                .append(escapeHtml(eng))
                .append("</label>");
        }
        html.append("</div>");

        // ── Finding Type filter — independent from Engine, and independent from
        // Impact/severity. "Needs Review" means the finding requires human
        // confirmation (axe-core "incomplete" rules, or an interaction-layer issue
        // with needsReview=true) — it is never encoded into the impact/severity
        // value. Both types are visible by default; suppressed findings are shown
        // separately in the "Verified False Positives" section, never mixed in here.
        html.append("<div class='engine-filter' id='a11yFindingTypeFilter' "
                + "style='margin:0;border-radius:0;border-left:none;border-right:none;"
                + "padding:10px 24px;background:#FFF8FB;border-bottom:2px solid #D4006E;'>")
            .append("<span class='filter-label'>&#128269; Finding Type &nbsp;"
                + "<span style='font-size:11px;font-weight:400;color:#888;'>"
                + "(applies to all sections below)</span>:</span>");
        for (String ft : new String[]{"VIOLATION", "NEEDS_REVIEW"}) {
            String label = "VIOLATION".equals(ft) ? "Violation" : "Needs Review";
            html.append("<label class='eng-chip' style='background:#FEF0F7;border-color:#D4006E;"
                    + "color:#D4006E;font-weight:700;'>")
                .append("<input type='checkbox' class='ft-chk' value='")
                .append(escapeHtml(ft)).append("' checked ")
                .append("onchange=\"a11yEngineToggle(this)\">")
                .append(escapeHtml(label))
                .append("</label>");
        }
        html.append("</div>");

        // ── Main content ─────────────────────────────────────────────────────
        html.append("<div class='content'>");

        // "Floor check, not a certification" methodology notice — axe-core (and this
        // report) catch a meaningful subset of WCAG failures, not all of them; manual
        // review by an accessibility SME is still required for full conformance.
        html.append("<div class='noise-banner' style='border-left-color:#1A1A1A;'>")
            .append("<b>&#9888; Floor Check, Not a Certification &nbsp;&mdash;&nbsp;</b> ")
            .append("Automated scanning (axe-core plus this suite's custom interaction/structural checks) ")
            .append("typically detects roughly 30&ndash;40% of WCAG conformance issues. ")
            .append("A clean report establishes a baseline floor of accessibility quality; it is ")
            .append("<b>not</b> a substitute for manual review, assistive-technology testing, or a full audit.")
            .append("</div>");

        // Verified false positives — suppressed from counted totals, still fully visible.
        appendSuppressionSection(html, scans);

        // Noise suppression notice
        if (noiseActive) {
            html.append("<div class='noise-banner'>")
                .append("<b>&#9432; Session Noise Config (Informational) &nbsp;&mdash;&nbsp;</b> ")
                .append("The following settings suppress alerts during live test execution ")
                .append("but <b>all violations are shown in this report</b> to match the Excel report. ");
            if (threshold != A11ySessionManager.ImpactThreshold.MINOR) {
                html.append("Impact threshold: <b>").append(threshold).append("</b> &nbsp;|&nbsp; ");
            }
            if (!allowedRules.isEmpty()) {
                html.append("Allowed rules: <b>").append(escapeHtml(String.join(", ", allowedRules))).append("</b>");
            }
            html.append("</div>");
        }

        // ── KPI cards ────────────────────────────────────────────────────────
        html.append("<div class='kpi-grid'>")
            .append(kpiCard("Total Scans",  String.valueOf(totalScans), "c-info", null))
            .append(kpiCard("PASS",         String.valueOf(pass),       "c-pass", "kpi-pass"))
            .append(kpiCard("FAIL",         String.valueOf(fail),       "c-fail", "kpi-fail"))
            .append(kpiCard("SKIPPED",      String.valueOf(skipped),    "c-skip", null))
            .append(kpiCard("ERROR",        String.valueOf(error),      "c-error", null))
            .append(kpiCard("Violations",   String.valueOf(totalViolations), "c-fail", "kpi-violations"))
            .append(kpiCard("Needs Review", String.valueOf(totalNeedsReview), "c-info", "kpi-needs-review"))
            .append(kpiCard("Unique Rules", String.valueOf(ruleCounts.size()), "c-info", "kpi-unique-rules"))
            .append("</div>");

        // ── Embed raw scan/violation data for the Engine Filter's live KPI recompute ──
        // Assign each scan a stable index so its Recent-Scans row / Scan-Details card can
        // be located and updated in place by the filter regardless of render/sort order.
        for (int si = 0; si < scans.size(); si++) {
            scans.get(si).scanIndex = si;
        }
        html.append("<script>var a11yRawScans=[");
        for (int si = 0; si < scans.size(); si++) {
            ScanRecord s = scans.get(si);
            boolean isSkipped = "SKIPPED".equalsIgnoreCase(s.outcome);
            boolean isError   = "ERROR".equalsIgnoreCase(s.outcome);
            html.append("{skipped:").append(isSkipped)
                .append(",error:").append(isError)
                .append(",pageName:'").append(escapeJsString(s.pageName)).append("'")
                .append(",violations:[");
            for (int vi = 0; vi < s.violations.size(); vi++) {
                ViolationDetail v = s.violations.get(vi);
                String eng = (v.engine == null || v.engine.trim().isEmpty()) ? "axe-core" : v.engine;
                String imp = (v.impact == null ? "UNKNOWN" : v.impact).toLowerCase(Locale.ROOT);
                String ft  = (v.findingType == null ? "VIOLATION" : v.findingType);
                html.append("{engine:'").append(escapeJsString(eng))
                    .append("',impact:'").append(escapeJsString(imp))
                    .append("',findingType:'").append(escapeJsString(ft))
                    .append("',ruleId:'").append(escapeJsString(v.ruleId)).append("'}");
                if (vi < s.violations.size() - 1) html.append(",");
            }
            html.append("]}");
            if (si < scans.size() - 1) html.append(",");
        }
        html.append("];</script>");

        // ── Impact Distribution ───────────────────────────────────────────────
        html.append("<div class='section'>")
            .append("<div class='section-head'>&#9632; Impact Distribution</div>")
            .append("<table><thead><tr><th>Impact</th><th>Occurrences</th></tr></thead><tbody id='impact-body'>");
        appendImpactRow(html, "CRITICAL", impactCounts.getOrDefault("CRITICAL", 0));
        appendImpactRow(html, "SERIOUS",  impactCounts.getOrDefault("SERIOUS",  0));
        appendImpactRow(html, "MODERATE", impactCounts.getOrDefault("MODERATE", 0));
        appendImpactRow(html, "MINOR",    impactCounts.getOrDefault("MINOR",    0));
        appendImpactRow(html, "UNKNOWN",  impactCounts.getOrDefault("UNKNOWN",  0));
        html.append("</tbody></table></div>");

        // ── Cross-page deduplicated violations ───────────────────────────────
        html.append("<div class='section'>")
            .append("<div class='section-head'>&#9632; Cross-Page Violations &mdash; Deduplicated</div>")
            .append("<div class='section-subtext'>Same rule grouped across all pages &mdash; ")
            .append("shows breadth of each issue without repeating identical rows.</div>")
            .append("<table><thead><tr>")
            .append("<th>Rule ID</th><th>Pages Affected</th><th>Total Occurrences</th>")
            .append("</tr></thead><tbody id='cross-page-body'>");
        if (dedupRules.isEmpty()) {
            html.append("<tr><td colspan='3' style='color:#888;font-style:italic;'>")
                .append("No violations captured.</td></tr>");
        } else {
            for (Map.Entry<String, Set<String>> entry : dedupRules) {
                int pages = entry.getValue().size();
                int occ   = ruleCounts.getOrDefault(entry.getKey(), 0);
                html.append("<tr>")
                    .append("<td><code style='font-size:12px;color:#D4006E;'>")
                    .append(escapeHtml(entry.getKey())).append("</code></td>")
                    .append("<td><b>").append(pages).append("</b></td>")
                    .append("<td>").append(occ).append("</td>")
                    .append("</tr>");
            }
        }
        html.append("</tbody></table></div>");

        // ── Top Rule IDs ─────────────────────────────────────────────────────
        html.append("<div class='section'>")
            .append("<div class='section-head'>&#9632; Top Rule IDs by Occurrence</div>")
            .append("<table><thead><tr><th>Rule ID</th><th>Occurrences</th></tr></thead><tbody id='top-rules-body'>");
        if (topRules.isEmpty()) {
            html.append("<tr><td colspan='2' style='color:#888;font-style:italic;'>")
                .append("No violations captured.</td></tr>");
        } else {
            for (Map.Entry<String, Integer> e : topRules) {
                html.append("<tr>")
                    .append("<td><code style='font-size:12px;color:#D4006E;'>")
                    .append(escapeHtml(e.getKey())).append("</code></td>")
                    .append("<td>").append(e.getValue()).append("</td></tr>");
            }
        }
        html.append("</tbody></table></div>");

        // ── Top pages by violations ───────────────────────────────────────────
        html.append("<div class='section'>")
            .append("<div class='section-head'>&#9632; Top Pages by Violations</div>")
            .append("<table><thead><tr><th>Page</th><th>Violations</th></tr></thead><tbody id='top-pages-body'>");
        if (topPages.isEmpty()) {
            html.append("<tr><td colspan='2' style='color:#888;font-style:italic;'>")
                .append("No scan artifacts found.</td></tr>");
        } else {
            for (Map.Entry<String, Integer> e : topPages) {
                html.append("<tr><td class='truncate'>").append(escapeHtml(e.getKey()))
                    .append("</td><td>").append(e.getValue()).append("</td></tr>");
            }
        }
        html.append("</tbody></table></div>");

        // ── Recent Scans ─────────────────────────────────────────────────────
        html.append("<div class='section'>")
            .append("<div class='section-head'>&#9632; Recent Scans</div>")
            .append("<table><thead><tr>")
            .append("<th>Timestamp</th><th>Page</th><th>Outcome</th><th>Violations</th>")
            .append("</tr></thead><tbody>");
        if (scans.isEmpty()) {
            html.append("<tr><td colspan='4' style='color:#888;font-style:italic;'>")
                .append("No accessibility scan artifacts found for this run.</td></tr>");
        } else {
            scans.stream()
                .sorted(Comparator.comparing((ScanRecord s) -> s.timestamp).reversed())
                .limit(50)
                .forEach(scan -> {
                    String pillClass = outcomePill(scan.outcome);
                    html.append("<tr data-scan-idx='").append(scan.scanIndex).append("'>")
                        .append("<td style='white-space:nowrap;font-size:12px;color:#888;'>")
                        .append(escapeHtml(scan.timestamp)).append("</td>")
                        .append("<td class='truncate'>").append(escapeHtml(scan.pageName)).append("</td>")
                        .append("<td><span class='pill ").append(pillClass)
                        .append("' id='rs-pill-").append(scan.scanIndex).append("'>")
                        .append(escapeHtml(scan.outcome)).append("</span></td>")
                        .append("<td id='rs-vio-").append(scan.scanIndex).append("'>")
                        .append(scan.violationCount > 0
                            ? "<b style='color:#D4006E;'>" + scan.violationCount + "</b>"
                            : "<span style='color:#28A745;'>0</span>")
                        .append("</td></tr>");
                });
        }
        html.append("</tbody></table></div>");

        // ── NEW: Interactive drilldown sections ───────────────────────────────
        appendViolationsDrilldown(html, scans);
        appendScanDetails(html, scans);
        appendAllIssuesTable(html, scans);

        // ── Inline JS for toggle-all + live search + global engine/finding-type filter ────
        html.append("<script>")
            .append("function a11yToggleAll(id,open){")
            .append("  var root=document.getElementById(id);if(!root)return;")
            .append("  root.querySelectorAll('details').forEach(function(d){d.open=open;});}")
            // Global engine + finding-type filter — applies to every [data-engine] element
            // on the page. Engine and Finding Type are independent dimensions (AND across
            // dimensions, OR within each dimension's checked chips). Report-only: recomputes
            // what is displayed; never mutates a11yRawScans, suppression state, or the
            // underlying artifacts/enforcement outcome.
            .append("function a11yApplyEngineFilter(){")
            .append("  var tbl=document.getElementById('a11yIssuesTable');")
            .append("  var engChks=document.querySelectorAll('#a11yEngineFilter .eng-chk');")
            .append("  var engEnabled=[];")
            .append("  engChks.forEach(function(c){if(c.checked)engEnabled.push(c.value.toLowerCase());});")
            .append("  var engAll=engChks.length===0||engEnabled.length===engChks.length;")
            .append("  function engMatch(eng){")
            .append("    if(engAll)return true;")
            .append("    eng=(eng||'').toLowerCase();")
            .append("    return engEnabled.some(function(e){return eng===e||eng.indexOf(e+':')===0;});")
            .append("  }")
            .append("  var ftChks=document.querySelectorAll('#a11yFindingTypeFilter .ft-chk');")
            .append("  var ftEnabled=[];")
            .append("  ftChks.forEach(function(c){if(c.checked)ftEnabled.push(c.value.toUpperCase());});")
            .append("  var ftAll=ftChks.length===0||ftEnabled.length===ftChks.length;")
            .append("  function ftMatch(ft){")
            .append("    if(ftAll)return true;")
            .append("    ft=(ft||'VIOLATION').toUpperCase();")
            .append("    return ftEnabled.indexOf(ft)!==-1;")
            .append("  }")
            .append("  var all=engAll&&ftAll;")
            // Show/hide individual violation cards and rows
            .append("  document.querySelectorAll('[data-engine]').forEach(function(el){")
            .append("    var show=engMatch(el.getAttribute('data-engine'))&&ftMatch(el.getAttribute('data-finding-type'));")
            .append("    el.style.display=show?'':'none';")
            .append("  });")
            // Hide impact-group <details> if ALL its v-cards are hidden
            .append("  document.querySelectorAll('.impact-group').forEach(function(grp){")
            .append("    var cards=grp.querySelectorAll('.v-card');")
            .append("    var anyVis=false;")
            .append("    cards.forEach(function(c){if(c.style.display!=='none')anyVis=true;});")
            .append("    grp.style.display=cards.length===0||anyVis?'':'none';")
            .append("  });")
            // Hide scan-card <details> if ALL its data-engine rows are hidden
            .append("  document.querySelectorAll('.scan-card').forEach(function(card){")
            .append("    var rows=card.querySelectorAll('tr[data-engine]');")
            .append("    if(rows.length===0)return;")
            .append("    var anyVis=false;")
            .append("    rows.forEach(function(r){if(r.style.display!=='none')anyVis=true;});")
            .append("    card.style.display=anyVis?'':'none';")
            .append("  });")
            // Auto-expand drilldown sections so the user sees the filtered result
            .append("  if(!all){")
            .append("    var dd=document.getElementById('sec-drilldown');if(dd)dd.open=true;")
            .append("    var sc=document.getElementById('sec-scans');if(sc)sc.open=true;")
            .append("    var ai=document.getElementById('sec-allissues');if(ai)ai.open=true;")
            .append("  }")
            // ── Recalculate KPI cards and Impact Distribution from raw scan data ──
            .append("  var totalVio=0,totalNeedsReview=0,passCount=0,failCount=0;")
            .append("  var impacts={critical:0,serious:0,moderate:0,minor:0,unknown:0};")
            .append("  var ruleSet={};")
            .append("  if(typeof a11yRawScans!=='undefined'){")
            .append("    a11yRawScans.forEach(function(scan,idx){")
            .append("      if(scan.skipped||scan.error)return;")
            .append("      var filtered=scan.violations.filter(function(v){return engMatch(v.engine)&&ftMatch(v.findingType);});")
            .append("      totalVio+=filtered.length;")
            .append("      filtered.forEach(function(v){if((v.findingType||'VIOLATION').toUpperCase()==='NEEDS_REVIEW')totalNeedsReview++;});")
            .append("      var isPass=filtered.length===0;")
            .append("      if(isPass)passCount++;else failCount++;")
            .append("      filtered.forEach(function(v){")
            .append("        var imp=(v.impact||'unknown').toLowerCase();")
            .append("        if(impacts.hasOwnProperty(imp))impacts[imp]++;else impacts.unknown++;")
            .append("        if(v.ruleId)ruleSet[v.ruleId]=true;")
            .append("      });")
            // Update this scan's own PASS/FAIL badge + violation count in the
            // Recent Scans row and the Scan Details card so they reflect only
            // the currently selected engine(s)/finding type(s), not the combined outcome.
            .append("      var rp=document.getElementById('rs-pill-'+idx);")
            .append("      if(rp){rp.className='pill '+(isPass?'pill-pass':'pill-fail');rp.textContent=isPass?'PASS':'FAIL';}")
            .append("      var rv=document.getElementById('rs-vio-'+idx);")
            .append("      if(rv){rv.innerHTML=filtered.length>0")
            .append("        ?\"<b style='color:#D4006E;'>\"+filtered.length+\"</b>\"")
            .append("        :\"<span style='color:#28A745;'>0</span>\";}")
            .append("      var sp=document.getElementById('sc-pill-'+idx);")
            .append("      if(sp){sp.className='pill '+(isPass?'pill-pass':'pill-fail');sp.textContent=isPass?'PASS':'FAIL';}")
            .append("      var ss=document.getElementById('sc-status-'+idx);")
            .append("      if(ss){ss.innerHTML=filtered.length>0")
            .append("        ?\" <span style='color:#D4006E;font-weight:700;margin-left:4px;'>\"+filtered.length+\" violation(s)</span>\"")
            .append("        :\" <span style='color:#28A745;margin-left:4px;'>&#10003; Clean</span>\";}")
            .append("    });")
            .append("  }")
            .append("  var kp=document.getElementById('kpi-pass');if(kp)kp.textContent=passCount;")
            .append("  var kf=document.getElementById('kpi-fail');if(kf)kf.textContent=failCount;")
            .append("  var kv=document.getElementById('kpi-violations');if(kv)kv.textContent=totalVio;")
            .append("  var knr=document.getElementById('kpi-needs-review');if(knr)knr.textContent=totalNeedsReview;")
            .append("  var ku=document.getElementById('kpi-unique-rules');if(ku)ku.textContent=Object.keys(ruleSet).length;")
            .append("  ['critical','serious','moderate','minor','unknown'].forEach(function(imp){")
            .append("    var el=document.getElementById('impact-cnt-'+imp);")
            .append("    if(el)el.textContent=impacts[imp]||0;")
            .append("    var ig=document.getElementById('ig-count-'+imp);")
            .append("    if(ig)ig.textContent=impacts[imp]||0;")
            .append("  });")
            .append("  var ddt=document.getElementById('drilldown-total-count');if(ddt)ddt.textContent=totalVio;")
            // ── Rebuild Cross-Page, Top Rules, Top Pages tables ──
            .append("  var ruleToPages={},ruleOcc={},pageVio={};")
            .append("  if(typeof a11yRawScans!=='undefined'){")
            .append("    a11yRawScans.forEach(function(scan){")
            .append("      if(scan.skipped||scan.error)return;")
            .append("      var pn=scan.pageName||'';")
            .append("      scan.violations.filter(function(v){return engMatch(v.engine)&&ftMatch(v.findingType);}).forEach(function(v){")
            .append("        var rid=v.ruleId||'';")
            .append("        if(!ruleToPages[rid])ruleToPages[rid]={};")
            .append("        ruleToPages[rid][pn]=true;")
            .append("        ruleOcc[rid]=(ruleOcc[rid]||0)+1;")
            .append("        pageVio[pn]=(pageVio[pn]||0)+1;")
            .append("      });")
            .append("    });")
            .append("  }")
            // Cross-Page tbody
            .append("  var cpb=document.getElementById('cross-page-body');")
            .append("  if(cpb){")
            .append("    var cpRows=Object.keys(ruleToPages).map(function(rid){")
            .append("      return {rid:rid,pages:Object.keys(ruleToPages[rid]).length,occ:ruleOcc[rid]||0};")
            .append("    }).sort(function(a,b){return b.pages-a.pages||a.rid.localeCompare(b.rid);}).slice(0,15);")
            .append("    cpb.innerHTML=cpRows.length===0")
            .append("      ?\"<tr><td colspan='3' style='color:#888;font-style:italic;'>No violations captured.</td></tr>\"")
            .append("      :cpRows.map(function(r){return \"<tr><td><code style='font-size:12px;color:#D4006E;'>\"+r.rid+\"</code></td><td><b>\"+r.pages+\"</b></td><td>\"+r.occ+\"</td></tr>\";}).join('');")
            .append("  }")
            // Top Rules tbody
            .append("  var trb=document.getElementById('top-rules-body');")
            .append("  if(trb){")
            .append("    var trRows=Object.keys(ruleOcc).map(function(rid){return{rid:rid,cnt:ruleOcc[rid]};}).sort(function(a,b){return b.cnt-a.cnt;}).slice(0,10);")
            .append("    trb.innerHTML=trRows.length===0")
            .append("      ?\"<tr><td colspan='2' style='color:#888;font-style:italic;'>No violations captured.</td></tr>\"")
            .append("      :trRows.map(function(r){return \"<tr><td><code style='font-size:12px;color:#D4006E;'>\"+r.rid+\"</code></td><td>\"+r.cnt+\"</td></tr>\";}).join('');")
            .append("  }")
            // Top Pages tbody
            .append("  var tpb=document.getElementById('top-pages-body');")
            .append("  if(tpb){")
            .append("    var tpRows=Object.keys(pageVio).map(function(p){return{page:p,cnt:pageVio[p]};}).sort(function(a,b){return b.cnt-a.cnt;}).slice(0,10);")
            .append("    tpb.innerHTML=tpRows.length===0")
            .append("      ?\"<tr><td colspan='2' style='color:#888;font-style:italic;'>No scan artifacts found.</td></tr>\"")
            .append("      :tpRows.map(function(r){return \"<tr><td class='truncate'>\"+r.page+\"</td><td>\"+r.cnt+\"</td></tr>\";}).join('');")
            .append("  }")
            .append("  if(tbl){")
            .append("    var vis=0;tbl.querySelectorAll('tbody tr').forEach(function(r){if(r.style.display!=='none')vis++;});")
            .append("    var c=document.getElementById('a11yCount');if(c)c.textContent=vis+' issue(s)';")
            .append("    var tc=document.getElementById('a11yTotalCount');if(tc)tc.textContent=vis;}")
            .append("}")
            .append("function a11yFilter(inputId,tableId,countId){")
            .append("  var val=document.getElementById(inputId).value.toLowerCase();")
            .append("  var engChks=document.querySelectorAll('#a11yEngineFilter .eng-chk');")
            .append("  var engEnabled=[];")
            .append("  engChks.forEach(function(c){if(c.checked)engEnabled.push(c.value.toLowerCase());});")
            .append("  var engAll=engChks.length===0||engEnabled.length===engChks.length;")
            .append("  var ftChks=document.querySelectorAll('#a11yFindingTypeFilter .ft-chk');")
            .append("  var ftEnabled=[];")
            .append("  ftChks.forEach(function(c){if(c.checked)ftEnabled.push(c.value.toUpperCase());});")
            .append("  var ftAll=ftChks.length===0||ftEnabled.length===ftChks.length;")
            .append("  var rows=document.getElementById(tableId).querySelectorAll('tbody tr');")
            .append("  var vis=0;")
            .append("  rows.forEach(function(r){")
            .append("    var eng=(r.getAttribute('data-engine')||'').toLowerCase();")
            .append("    var ft=(r.getAttribute('data-finding-type')||'VIOLATION').toUpperCase();")
            .append("    var engMatch=engAll||engEnabled.some(function(e){return eng===e||eng.indexOf(e+':')===0;});")
            .append("    var ftMatch=ftAll||ftEnabled.indexOf(ft)!==-1;")
            .append("    var show=engMatch&&ftMatch&&(!val||r.textContent.toLowerCase().indexOf(val)!==-1);")
            .append("    r.style.display=show?'':'none'; if(show)vis++;")
            .append("  });")
            .append("  var c=document.getElementById(countId);if(c)c.textContent=vis+' issue(s)';}")
            .append("function a11yEngineToggle(chk){")
            .append("  a11yApplyEngineFilter();")
            // also re-run text search so counts stay correct
            .append("  var si=document.getElementById('a11ySearch');")
            .append("  if(si)a11yFilter('a11ySearch','a11yIssuesTable','a11yCount');")
            .append("  var chip=chk.closest('.eng-chip');")
            .append("  if(chip){chip.style.background=chk.checked?'#FEF0F7':'#F9F9F9';"
                    + "chip.style.borderColor=chk.checked?'#D4006E':'#CCC';"
                    + "chip.style.color=chk.checked?'#D4006E':'#555';"
                    + "chip.style.fontWeight=chk.checked?'700':'400';}}")
            .append("</script>");

        html.append("</div>"); // end .content

        // ── Footer ───────────────────────────────────────────────────────────
        html.append("<footer class='site-footer'>")
            .append("<div class='footer-logo'>")
            .append("<span class='nyc'>NYC</span><span class='oti'>OTI</span>")
            .append("</div>")
            .append("<div class='footer-meta'>")
            .append("Office of Technology &amp; Innovation &nbsp;&bull;&nbsp; ")
            .append("Accessibility Report &nbsp;&bull;&nbsp; ")
            .append(escapeHtml(projectName)).append(" &nbsp;&bull;&nbsp; ")
            .append("Generated ").append(escapeHtml(generatedAt))
            .append("</div>")
            .append("</footer>")
            .append("</body></html>");

        return html.toString();
    }

    /**
     * Resolves a human-readable project/repository name for the report header.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>{@code accessibility.project.name} from config (JVM prop / env / properties file)</li>
     *   <li>the working-directory folder name (typically the repo/checkout folder)</li>
     *   <li>{@code "(unknown project)"} as a last resort</li>
     * </ol>
     */
    static String resolveProjectName() {
        String configured = A11yConfig.get("accessibility.project.name");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        try {
            Path cwd = Paths.get(System.getProperty("user.dir", "."))
                    .toAbsolutePath().normalize();
            Path name = cwd.getFileName();
            if (name != null && !name.toString().trim().isEmpty()) {
                return name.toString();
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return "(unknown project)";
    }

    /**
     * @param valueId optional {@code id} attribute placed on the value element, used by the
     *                Engine Filter's client-side KPI recompute (see {@code a11yApplyEngineFilter}
     *                in {@link #buildHtml}); pass {@code null} for KPIs it does not recompute.
     */
    private static String kpiCard(String label, String value, String cssClass, String valueId) {
        String idAttr = (valueId == null || valueId.isEmpty()) ? "" : " id='" + valueId + "'";
        return "<div class='kpi-card " + cssClass + "'>"
             + "<div class='kpi-label'>" + escapeHtml(label) + "</div>"
             + "<div class='kpi-value'" + idAttr + ">" + escapeHtml(value) + "</div>"
             + "</div>";
    }

    /**
     * Renders the "Verified False Positives" section: every violation excluded from
     * counted totals by {@link A11ySuppressionRegistry}, together with its audit trail
     * (reason / verified-on / expires-on). Also flags any expired suppression entries
     * so a reader knows exactly which judgements need to be re-verified.
     */
    private static void appendSuppressionSection(StringBuilder html, List<ScanRecord> scans) {
        Map<String, List<String>> suppressedPagesByRule = new LinkedHashMap<>();
        for (ScanRecord scan : scans) {
            for (ViolationDetail vd : scan.suppressedViolations) {
                suppressedPagesByRule.computeIfAbsent(vd.ruleId, k -> new ArrayList<>()).add(scan.pageName);
            }
        }

        Collection<A11ySuppressionRegistry.Entry> expired = A11ySuppressionRegistry.getExpiredEntries();
        if (!expired.isEmpty()) {
            html.append("<div class='noise-banner' style='border-left-color:#DC3545;'>")
                .append("<b>&#9888; Suppressions Needing Re-Verification &nbsp;&mdash;&nbsp;</b> ")
                .append("The following verified false-positive suppressions have expired and no longer ")
                .append("suppress their rule (violations count normally again): <b>")
                .append(escapeHtml(expired.stream().map(e -> e.ruleId).collect(Collectors.joining(", "))))
                .append("</b>. Re-review and refresh their <code>verified</code>/<code>expires</code> dates if still valid.")
                .append("</div>");
        }

        if (suppressedPagesByRule.isEmpty()) {
            return;
        }

        html.append("<div class='section'>")
            .append("<div class='section-head'>&#9632; Verified False Positives &mdash; Not Counted Toward Score</div>")
            .append("<div class='section-subtext'>Reviewed and justified by a human; excluded from PASS/FAIL ")
            .append("and violation totals above, but never hidden from this report.</div>")
            .append("<table><thead><tr>")
            .append("<th>Rule ID</th><th>Pages Affected</th><th>Reason</th><th>Verified</th><th>Expires</th>")
            .append("</tr></thead><tbody>");
        for (Map.Entry<String, List<String>> e : suppressedPagesByRule.entrySet()) {
            A11ySuppressionRegistry.Entry entry = A11ySuppressionRegistry.get(e.getKey());
            String reason   = entry != null ? entry.reason : "(no longer registered)";
            String verified = entry != null ? entry.verifiedOn.toString() : "";
            String expires  = entry != null ? entry.expiresOn.toString() : "";
            Set<String> pages = new LinkedHashSet<>(e.getValue());
            html.append("<tr>")
                .append("<td><code style='font-size:12px;color:#D4006E;'>")
                .append(escapeHtml(e.getKey())).append("</code></td>")
                .append("<td>").append(pages.size()).append("</td>")
                .append("<td>").append(escapeHtml(reason)).append("</td>")
                .append("<td>").append(escapeHtml(verified)).append("</td>")
                .append("<td>").append(escapeHtml(expires)).append("</td>")
                .append("</tr>");
        }
        html.append("</tbody></table></div>");
    }

    private static void appendImpactRow(StringBuilder html, String impact, int count) {
        String pillClass = "pill-" + impact.toLowerCase();
        html.append("<tr>")
            .append("<td><span class='pill ").append(pillClass).append("'>")
            .append(escapeHtml(impact)).append("</span></td>")
            .append("<td id='impact-cnt-").append(impact.toLowerCase(Locale.ROOT)).append("'>")
            .append(count).append("</td>")
            .append("</tr>");
    }

    private static String outcomePill(String outcome) {
        if (outcome == null) return "pill-unknown";
        switch (outcome.toUpperCase()) {
            case "PASS":    return "pill-pass";
            case "FAIL":    return "pill-fail";
            case "SKIPPED": return "pill-skipped";
            case "ERROR":   return "pill-error";
            default:        return "pill-unknown";
        }
    }

    private static String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escapes a string for safe embedding inside a single-quoted inline {@code <script>} JS literal. */
    private static String escapeJsString(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\r", "").replace("\n", "\\n");
    }

    // ── Private static helpers ────────────────────────────────────────────────

    /** Truncates a string to {@code max} chars, appending {@code …} if clipped. */
    private static String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > max ? s.substring(0, max) + "\u2026" : s;
    }

    /**
     * Renders the "Violations Drilldown — By Impact" expandable section.
     * Groups every individual violation occurrence from every scan by impact
     * severity (CRITICAL → SERIOUS → MODERATE → MINOR → UNKNOWN). Each
     * violation is a nested {@code <details>} card showing page, description,
     * help URL, and up to 5 affected element snippets.
     */
    private static void appendViolationsDrilldown(StringBuilder html, List<ScanRecord> scans) {
        // Build impact-ordered map: impact → list of violation entries
        Map<String, List<Object[]>> byImpact = new LinkedHashMap<>();
        for (String imp : new String[]{"CRITICAL", "SERIOUS", "MODERATE", "MINOR", "UNKNOWN"}) {
            byImpact.put(imp, new ArrayList<>());
        }
        for (ScanRecord scan : scans) {
            for (ViolationDetail v : scan.violations) {
                String imp = (v.impact == null || v.impact.trim().isEmpty()) ? "UNKNOWN"
                        : v.impact.toUpperCase(Locale.ROOT);
                byImpact.computeIfAbsent(imp, k -> new ArrayList<>())
                        .add(new Object[]{ scan.pageName, scan.timestamp, v });
            }
        }
        long total = byImpact.values().stream().mapToLong(List::size).sum();
        if (total == 0) return;

        html.append("<details class='section' id='sec-drilldown'>")
            .append("<summary class='section-head'>&#9632; Violations Drilldown &mdash; By Impact")
            .append("<span style='font-weight:400;font-size:12px;margin-left:10px;opacity:.8;'>")
            .append("<span id='drilldown-total-count'>").append(total).append("</span>")
            .append(" total occurrence(s) across all scans &mdash; click to expand")
            .append("</span></summary>")
            .append("<div style='padding:4px 0 0;'>")
            .append("<div class='toggle-btns'>")
            .append("<button class='toggle-btn' onclick=\"a11yToggleAll('sec-drilldown',true)\">&#9660; Expand All</button>")
            .append("<button class='toggle-btn' onclick=\"a11yToggleAll('sec-drilldown',false)\">&#9650; Collapse All</button>")
            .append("</div>");

        String[] impactOrder   = {"CRITICAL", "SERIOUS", "MODERATE", "MINOR", "UNKNOWN"};
        String[] igClasses     = {"ig-critical", "ig-serious", "ig-moderate", "ig-minor", "ig-unknown"};
        String[] pillClasses   = {"pill-critical", "pill-serious", "pill-moderate", "pill-minor", "pill-unknown"};

        for (int i = 0; i < impactOrder.length; i++) {
            String imp = impactOrder[i];
            List<Object[]> entries = byImpact.get(imp);
            if (entries == null || entries.isEmpty()) continue;

            boolean openGroup = "CRITICAL".equals(imp) || "SERIOUS".equals(imp);
            html.append("<details class='impact-group ").append(igClasses[i]).append("'")
                .append(openGroup ? " open" : "").append(">")
                .append("<summary>")
                .append("<span class='pill ").append(pillClasses[i]).append("'>").append(imp).append("</span>")
                .append(" <b id='ig-count-").append(imp.toLowerCase(Locale.ROOT)).append("'>")
                .append(entries.size()).append("</b> occurrence(s)")
                .append("</summary>")
                .append("<div class='ig-pad'>");

            for (Object[] e : entries) {
                String pageName = (String) e[0];
                String ts       = (String) e[1];
                ViolationDetail v = (ViolationDetail) e[2];
                String engine = (v.engine == null || v.engine.trim().isEmpty()) ? "axe-core" : v.engine;
                String findingType = (v.findingType == null || v.findingType.trim().isEmpty()) ? "VIOLATION" : v.findingType;

                html.append("<details class='v-card' data-engine='").append(escapeHtml(engine))
                    .append("' data-finding-type='").append(escapeHtml(findingType)).append("'>")
                    .append("<summary>")
                    .append("<code style='font-size:11px;color:#D4006E;background:#FEF0F7;"
                            + "padding:1px 6px;border-radius:3px;flex-shrink:0;'>")
                    .append(escapeHtml(v.ruleId)).append("</code>")
                    .append("<span style='color:#555;font-size:12px;flex-shrink:0;'>&nbsp;")
                    .append(escapeHtml(truncate(pageName, 60))).append("</span>")
                    .append("<span style='color:#888;font-size:12px;'>&mdash;&nbsp;")
                    .append(escapeHtml(truncate(v.description, 110))).append("</span>")
                    .append("</summary>")
                    .append("<div class='v-card-body'>");

                html.append("<div><span class='v-label'>Page:</span> ").append(escapeHtml(pageName)).append("</div>")
                    .append("<div><span class='v-label'>Timestamp:</span> <span style='color:#888;'>")
                    .append(escapeHtml(ts)).append("</span></div>");
                if ("NEEDS_REVIEW".equals(findingType)) {
                    html.append("<div><span class='v-label'>Finding Type:</span> "
                            + "<span class='pill pill-needs_review'>NEEDS REVIEW</span></div>");
                }
                if (!v.description.trim().isEmpty()) {
                    html.append("<div><span class='v-label'>Description:</span> ").append(escapeHtml(v.description)).append("</div>");
                }
                if (!v.help.trim().isEmpty()) {
                    html.append("<div><span class='v-label'>Help:</span> ").append(escapeHtml(v.help)).append("</div>");
                }
                if (!v.helpUrl.trim().isEmpty()) {
                    html.append("<div><span class='v-label'>Learn More:</span> <a href='")
                        .append(escapeHtml(v.helpUrl)).append("' target='_blank' style='color:#D4006E;'>")
                        .append(escapeHtml(v.helpUrl)).append("</a></div>");
                }
                if (!v.affectedElements.isEmpty()) {
                    html.append("<div style='margin-top:6px;'><span class='v-label'>Affected Elements</span>"
                            + "<span style='color:#888;font-size:11px;'>(").append(v.affectedElements.size())
                        .append(" total, showing up to 5):</span></div>");
                    v.affectedElements.stream().limit(5).forEach(el ->
                        html.append("<code class='el-code'>").append(escapeHtml(el)).append("</code>")
                    );
                }
                html.append("</div></details>"); // v-card
            }
            html.append("</div></details>"); // impact-group
        }
        html.append("</div></details>"); // section
    }

    /**
     * Renders the "Scan Details — Per Page" expandable section.
     * One collapsible card per scan (newest first). Each card shows the full
     * violation table for that scan — mirrors the Excel "Scan History" sheet
     * combined with per-scan violation rows from "All Issues".
     */
    private static void appendScanDetails(StringBuilder html, List<ScanRecord> scans) {
        if (scans.isEmpty()) return;

        List<ScanRecord> sorted = scans.stream()
                .sorted(Comparator.comparing((ScanRecord s) -> s.timestamp).reversed())
                .collect(Collectors.toList());

        html.append("<details class='section' id='sec-scans'>")
            .append("<summary class='section-head'>&#9632; Scan Details &mdash; Per Page")
            .append("<span style='font-weight:400;font-size:12px;margin-left:10px;opacity:.8;'>")
            .append(sorted.size()).append(" scan(s) &mdash; click to expand</span></summary>")
            .append("<div style='padding:4px 0 0;'>")
            .append("<div class='toggle-btns'>")
            .append("<button class='toggle-btn' onclick=\"a11yToggleAll('sec-scans',true)\">&#9660; Expand All</button>")
            .append("<button class='toggle-btn' onclick=\"a11yToggleAll('sec-scans',false)\">&#9650; Collapse All</button>")
            .append("</div>");

        for (ScanRecord scan : sorted) {
            String pillClass = outcomePill(scan.outcome);
            boolean hasFails  = scan.violationCount > 0;

            html.append("<details class='scan-card'>")
                .append("<summary>")
                .append("<span class='pill ").append(pillClass)
                .append("' id='sc-pill-").append(scan.scanIndex).append("'>")
                .append(escapeHtml(scan.outcome)).append("</span>")
                .append(" <b>").append(escapeHtml(scan.pageName)).append("</b>")
                .append(" <span style='color:#888;font-size:12px;font-weight:400;'>")
                .append(escapeHtml(scan.timestamp)).append("</span>")
                .append("<span id='sc-status-").append(scan.scanIndex).append("'>");
            if (hasFails) {
                html.append(" <span style='color:#D4006E;font-weight:700;margin-left:4px;'>")
                    .append(scan.violationCount).append(" violation(s)</span>");
            } else {
                html.append(" <span style='color:#28A745;margin-left:4px;'>&#10003; Clean</span>");
            }
            html.append("</span>");
            html.append("</summary><div>");

            if (scan.violations.isEmpty()) {
                html.append("<p style='padding:12px 16px;color:#28A745;font-size:13px;margin:0;'>"
                        + "&#10003; No violations found on this page.</p>");
            } else {
                html.append("<table style='margin:0;border-radius:0;'>"
                        + "<thead><tr>"
                        + "<th>Impact</th><th>Rule ID</th><th>Description</th>"
                        + "<th>Help</th><th>Affected Elements</th>"
                        + "</tr></thead><tbody>");
                for (ViolationDetail v : scan.violations) {
                    String impClass = "pill-" + v.impact.toLowerCase(Locale.ROOT);
                    String engine = (v.engine == null || v.engine.trim().isEmpty()) ? "axe-core" : v.engine;
                    String findingType = (v.findingType == null || v.findingType.trim().isEmpty()) ? "VIOLATION" : v.findingType;
                    html.append("<tr data-engine='").append(escapeHtml(engine))
                        .append("' data-finding-type='").append(escapeHtml(findingType)).append("'>")
                        .append("<td><span class='pill ").append(impClass).append("'>")
                        .append(escapeHtml(v.impact)).append("</span>");
                    if ("NEEDS_REVIEW".equals(findingType)) {
                        html.append(" <span class='pill pill-needs_review'>NEEDS REVIEW</span>");
                    }
                    html.append("</td>")
                        .append("<td><code style='font-size:11px;color:#D4006E;'>")
                        .append(escapeHtml(v.ruleId)).append("</code></td>")
                        .append("<td style='font-size:12px;'>").append(escapeHtml(v.description)).append("</td>")
                        .append("<td style='font-size:12px;white-space:nowrap;'>");
                    if (!v.helpUrl.trim().isEmpty()) {
                        html.append("<a href='").append(escapeHtml(v.helpUrl))
                            .append("' target='_blank' style='color:#D4006E;'>docs &#8599;</a>");
                    }
                    html.append("</td><td style='font-size:11px;font-family:monospace;'>");
                    v.affectedElements.stream().limit(3).forEach(el ->
                        html.append("<div style='background:#F5F5F5;padding:1px 5px;margin:1px 0;"
                                + "border-radius:2px;word-break:break-all;max-width:320px;overflow:hidden;"
                                + "text-overflow:ellipsis;white-space:nowrap;' title='")
                            .append(escapeHtml(el)).append("'>")
                            .append(escapeHtml(el)).append("</div>")
                    );
                    if (v.affectedElements.size() > 3) {
                        html.append("<span style='color:#888;font-size:10px;'>+")
                            .append(v.affectedElements.size() - 3).append(" more</span>");
                    }
                    html.append("</td></tr>");
                }
                html.append("</tbody></table>");
            }
            html.append("</div></details>"); // scan-card
        }
        html.append("</div></details>"); // section
    }

    /**
     * Renders the "All Issues — Complete List" expandable section.
     * Flat searchable table of every individual violation across all scans —
     * the HTML equivalent of the Excel "All Issues" sheet. Collapsed by default
     * to avoid slowing initial page load on large runs.
     */
    private static void appendAllIssuesTable(StringBuilder html, List<ScanRecord> scans) {
        long total = scans.stream().mapToLong(s -> s.violations.size()).sum();
        if (total == 0) return;

        html.append("<details class='section' id='sec-allissues'>")
            .append("<summary class='section-head'>&#9632; All Issues &mdash; Complete List (")
            .append(total).append(")</summary>")
            .append("<div style='padding:4px 0 0;'>")
            .append("<div class='search-wrap'>")
            .append("<input class='search-input' id='a11ySearch' type='text' ")
            .append("placeholder='Filter by rule ID, page, description, impact\u2026' ")
            .append("oninput=\"a11yFilter('a11ySearch','a11yIssuesTable','a11yCount')\">")
            .append("<span class='result-count' id='a11yCount'>").append(total).append(" issue(s)</span>")
            .append("<span class='result-count' id='a11yTotalCount' style='display:none;'>").append(total).append("</span>")
            .append("</div>")
            .append("<table id='a11yIssuesTable'><thead><tr>")
            .append("<th>#</th><th>Impact</th><th>Rule ID</th><th>Description</th>")
            .append("<th>Page</th><th>Affected Elements</th><th>Help</th><th>Timestamp</th>")
            .append("</tr></thead><tbody>");

        int rowNum = 0;
        for (ScanRecord scan : scans) {
            for (ViolationDetail v : scan.violations) {
                rowNum++;
                String impClass = "pill-" + v.impact.toLowerCase(Locale.ROOT);
                String engine = (v.engine == null || v.engine.trim().isEmpty()) ? "axe-core" : v.engine;
                String findingType = (v.findingType == null || v.findingType.trim().isEmpty()) ? "VIOLATION" : v.findingType;
                html.append("<tr data-engine='").append(escapeHtml(engine))
                    .append("' data-finding-type='").append(escapeHtml(findingType)).append("'>")
                    .append("<td style='color:#AAA;font-size:11px;'>").append(rowNum).append("</td>")
                    .append("<td><span class='pill ").append(impClass).append("'>")
                    .append(escapeHtml(v.impact)).append("</span>");
                if ("NEEDS_REVIEW".equals(findingType)) {
                    html.append(" <span class='pill pill-needs_review'>NEEDS REVIEW</span>");
                }
                html.append("</td>")
                    .append("<td><code style='font-size:11px;color:#D4006E;'>")
                    .append(escapeHtml(v.ruleId)).append("</code></td>")
                    .append("<td style='font-size:12px;max-width:280px;'>")
                    .append(escapeHtml(v.description)).append("</td>")
                    .append("<td class='truncate' style='font-size:12px;max-width:180px;'>")
                    .append(escapeHtml(scan.pageName)).append("</td>")
                    .append("<td style='font-size:11px;font-family:monospace;max-width:220px;'>");
                v.affectedElements.stream().limit(2).forEach(el ->
                    html.append("<div style='background:#F5F5F5;padding:1px 5px;margin:1px 0;"
                            + "border-radius:2px;word-break:break-all;overflow:hidden;"
                            + "max-width:210px;text-overflow:ellipsis;white-space:nowrap;' title='")
                        .append(escapeHtml(el)).append("'>")
                        .append(escapeHtml(el)).append("</div>")
                );
                if (v.affectedElements.size() > 2) {
                    html.append("<span style='color:#888;font-size:10px;'>+")
                        .append(v.affectedElements.size() - 2).append(" more</span>");
                }
                html.append("</td><td style='font-size:12px;'>");
                if (!v.helpUrl.trim().isEmpty()) {
                    html.append("<a href='").append(escapeHtml(v.helpUrl))
                        .append("' target='_blank' style='color:#D4006E;'>docs &#8599;</a>");
                }
                html.append("</td>")
                    .append("<td style='font-size:11px;color:#AAA;white-space:nowrap;'>")
                    .append(escapeHtml(scan.timestamp)).append("</td>")
                    .append("</tr>");
            }
        }
        html.append("</tbody></table></div></details>");
    }

    // ── Inner record classes ──────────────────────────────────────────────────

    private static final class ViolationDetail {
        String ruleId       = "";
        String impact       = "";
        String description  = "";
        String help         = "";
        String helpUrl      = "";
        /** Report-filter dimension: {@code "axe-core"} or {@code "Interaction:<checkId>"} — see {@link ScanRecord#engine}. */
        String engine       = "axe-core";
        /**
         * Independent classification dimension — {@code "VIOLATION"} (confirmed) or
         * {@code "NEEDS_REVIEW"} (requires human confirmation: axe-core {@code incomplete}
         * rules, or an interaction-layer issue with {@code needsReview=true}). Deliberately
         * NOT encoded into {@link #impact}: impact is a severity value
         * (CRITICAL/SERIOUS/MODERATE/MINOR/UNKNOWN) and must remain independently queryable
         * from finding type — see the "Finding Type Filter" section of {@link #buildHtml}.
         */
        String findingType  = "VIOLATION";
        final List<String> affectedElements = new ArrayList<>();
    }

    private static final class ScanRecord {
        String timestamp;
        String pageName;
        String outcome;
        int violationCount;
        /**
         * Which detection engine produced this scan's findings — {@code "axe-core"} for
         * standard {@code *_a11y.json} artifacts, or {@code "Interaction:<checkId>"} for
         * {@code *_interaction_*.json} (Layers 2-5: interaction/WCAG2.2/structural/motion)
         * artifacts. Drives the report-only "Engine Filter" UI (see {@link #buildHtml});
         * never affects violation counts, PASS/FAIL outcome, or suppression.
         */
        String engine = "axe-core";
        /** Stable index assigned in {@link #buildHtml} so the Engine Filter can update this scan's
         *  Recent-Scans row / Scan-Details card in place regardless of render/sort order. */
        int scanIndex;
        /** Count of {@link ViolationDetail} entries on this scan with {@code findingType=NEEDS_REVIEW}. */
        int needsReviewCount;
        final List<String>          ruleIds    = new ArrayList<>();
        final List<String>          impacts    = new ArrayList<>();
        final List<ViolationDetail> violations = new ArrayList<>();
        /** Violations excluded from counted totals by {@link A11ySuppressionRegistry} — still rendered, never counted. */
        final List<ViolationDetail> suppressedViolations = new ArrayList<>();
    }
}

