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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** Returns the configured report directory fresh on every call — never frozen. */
    private static Path reportDir() { return A11yConfig.outputDir(); }

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
        if (!Files.exists(reportDir())) {
            return Collections.emptyList();
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(reportDir())) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("_a11y.json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());
        }

        List<ScanRecord> records = new ArrayList<>();
        for (Path file : files) {
            try {
                JsonNode root = MAPPER.readTree(file.toFile());
                ScanRecord record = new ScanRecord();
                record.timestamp = root.path("timestamp").asText("");
                record.pageName = root.path("pageName").asText("(unknown)");
                record.outcome = root.path("outcome").asText("UNKNOWN");
                record.violationCount = root.path("violationCount").asInt(0);

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
                    vd.description = v.path("description").asText("");
                    vd.help        = v.path("help").asText("");
                    vd.helpUrl     = v.path("helpUrl").asText("");
                    for (JsonNode el : v.path("affectedElements")) {
                        String elText = el.asText("").trim();
                        if (!elText.trim().isEmpty()) vd.affectedElements.add(elText);
                    }
                    record.violations.add(vd);
                }
                records.add(record);
            } catch (Exception ex) {
                logger.debug("Skipping malformed accessibility artifact {}: {}", file, ex.getMessage());
            }
        }
        return records;
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
        int totalViolations = scans.stream().mapToInt(s -> s.violationCount).sum();

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
            .append("</div>")
            .append("</header>");

        // ── Hero ─────────────────────────────────────────────────────────────
        html.append("<div class='hero'>")
            .append("<h1>Accessibility Summary</h1>")
            .append("<div class='project-name'>").append(escapeHtml(projectName)).append("</div>")
            .append("<div class='meta'>")
            .append("<span>&#128197; Generated: ").append(escapeHtml(generatedAt)).append("</span>")
            .append("<span>&#128194; Source: ").append(escapeHtml(reportDir().toString())).append("</span>")
            .append("</div>")
            .append("</div>");

        // ── Main content ─────────────────────────────────────────────────────
        html.append("<div class='content'>");

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
            .append(kpiCard("Total Scans",  String.valueOf(totalScans), "c-info"))
            .append(kpiCard("PASS",         String.valueOf(pass),       "c-pass"))
            .append(kpiCard("FAIL",         String.valueOf(fail),       "c-fail"))
            .append(kpiCard("SKIPPED",      String.valueOf(skipped),    "c-skip"))
            .append(kpiCard("ERROR",        String.valueOf(error),      "c-error"))
            .append(kpiCard("Violations",   String.valueOf(totalViolations), "c-fail"))
            .append(kpiCard("Unique Rules", String.valueOf(ruleCounts.size()), "c-info"))
            .append("</div>");

        // ── Impact Distribution ───────────────────────────────────────────────
        html.append("<div class='section'>")
            .append("<div class='section-head'>&#9632; Impact Distribution</div>")
            .append("<table><thead><tr><th>Impact</th><th>Occurrences</th></tr></thead><tbody>");
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
            .append("</tr></thead><tbody>");
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
            .append("<table><thead><tr><th>Rule ID</th><th>Occurrences</th></tr></thead><tbody>");
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
            .append("<table><thead><tr><th>Page</th><th>Violations</th></tr></thead><tbody>");
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
                    html.append("<tr>")
                        .append("<td style='white-space:nowrap;font-size:12px;color:#888;'>")
                        .append(escapeHtml(scan.timestamp)).append("</td>")
                        .append("<td class='truncate'>").append(escapeHtml(scan.pageName)).append("</td>")
                        .append("<td><span class='pill ").append(pillClass).append("'>")
                        .append(escapeHtml(scan.outcome)).append("</span></td>")
                        .append("<td>")
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

        // ── Inline JS for toggle-all + live search ────────────────────────────
        html.append("<script>")
            .append("function a11yToggleAll(id,open){")
            .append("  var root=document.getElementById(id);if(!root)return;")
            .append("  root.querySelectorAll('details').forEach(function(d){d.open=open;});}")
            .append("function a11yFilter(inputId,tableId,countId){")
            .append("  var val=document.getElementById(inputId).value.toLowerCase();")
            .append("  var rows=document.getElementById(tableId).querySelectorAll('tbody tr');")
            .append("  var vis=0;")
            .append("  rows.forEach(function(r){")
            .append("    var show=!val||r.textContent.toLowerCase().indexOf(val)!==-1;")
            .append("    r.style.display=show?'':'none'; if(show)vis++;")
            .append("  });")
            .append("  var c=document.getElementById(countId);if(c)c.textContent=vis+' issue(s)';}")
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

    private static String kpiCard(String label, String value, String cssClass) {        return "<div class='kpi-card " + cssClass + "'>"
             + "<div class='kpi-label'>" + escapeHtml(label) + "</div>"
             + "<div class='kpi-value'>" + escapeHtml(value) + "</div>"
             + "</div>";
    }

    private static void appendImpactRow(StringBuilder html, String impact, int count) {
        String pillClass = "pill-" + impact.toLowerCase();
        html.append("<tr>")
            .append("<td><span class='pill ").append(pillClass).append("'>")
            .append(escapeHtml(impact)).append("</span></td>")
            .append("<td>").append(count).append("</td>")
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
            .append(total).append(" total occurrence(s) across all scans &mdash; click to expand")
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
                .append(" <b>").append(entries.size()).append("</b> occurrence(s)")
                .append("</summary>")
                .append("<div class='ig-pad'>");

            for (Object[] e : entries) {
                String pageName = (String) e[0];
                String ts       = (String) e[1];
                ViolationDetail v = (ViolationDetail) e[2];

                html.append("<details class='v-card'>")
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
                .append("<span class='pill ").append(pillClass).append("'>")
                .append(escapeHtml(scan.outcome)).append("</span>")
                .append(" <b>").append(escapeHtml(scan.pageName)).append("</b>")
                .append(" <span style='color:#888;font-size:12px;font-weight:400;'>")
                .append(escapeHtml(scan.timestamp)).append("</span>");
            if (hasFails) {
                html.append(" <span style='color:#D4006E;font-weight:700;margin-left:4px;'>")
                    .append(scan.violationCount).append(" violation(s)</span>");
            } else {
                html.append(" <span style='color:#28A745;margin-left:4px;'>&#10003; Clean</span>");
            }
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
                    html.append("<tr>")
                        .append("<td><span class='pill ").append(impClass).append("'>")
                        .append(escapeHtml(v.impact)).append("</span></td>")
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
                html.append("<tr>")
                    .append("<td style='color:#AAA;font-size:11px;'>").append(rowNum).append("</td>")
                    .append("<td><span class='pill ").append(impClass).append("'>")
                    .append(escapeHtml(v.impact)).append("</span></td>")
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
        final List<String> affectedElements = new ArrayList<>();
    }

    private static final class ScanRecord {
        String timestamp;
        String pageName;
        String outcome;
        int violationCount;
        final List<String>          ruleIds    = new ArrayList<>();
        final List<String>          impacts    = new ArrayList<>();
        final List<ViolationDetail> violations = new ArrayList<>();
    }
}

