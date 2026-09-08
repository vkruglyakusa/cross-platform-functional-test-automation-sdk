package com.test.automation.sdk.accessibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.automation.sdk.accessibility.config.A11yConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * AccessibilityExcelReporter — generates a multi-sheet Excel (.xlsx) report from ALL
 * per-scan artifacts produced by {@link AccessibilityChecker}.
 *
 * <p>Output: {@code <output>/accessibility-report_<timestamp>.xlsx}. Call once at the
 * end of a run (e.g. from a JUnit {@code @AfterAll} or TestNG {@code @AfterSuite}).</p>
 *
 * <h3>Artifact sources</h3>
 * <ul>
 *   <li>{@code *_a11y.json} — axe-core Layer 1 scan results</li>
 *   <li>{@code *_interaction_*.json} — Layers 2-5 findings (interaction, WCAG 2.2,
 *       structural, motion). Previously these were not read, causing silent data-loss
 *       of all non-axe violations in the final report.</li>
 * </ul>
 *
 * <h3>Sheets produced</h3>
 * <ol>
 *   <li><b>Summary</b> — run-level KPIs and impact distribution</li>
 *   <li><b>Scan History</b> — one row per scan (all engines)</li>
 *   <li><b>Violations Detail</b> — one row per violation (all engines)</li>
 * </ol>
 *
 * <h3>Relationship to {@link AccessibilityChecker#writeExcelReport()}</h3>
 * <p>{@code AccessibilityChecker.writeExcelReport()} produces a live-updating
 * {@code accessibility-report.xlsx} (2-sheet format) that is rewritten after every
 * individual scan. This reporter produces a definitive timestamped file at suite-end
 * with a richer 3-sheet format covering all engines. Both files land in the same
 * output directory.</p>
 */
public final class AccessibilityExcelReporter {

    private static final Logger logger = LoggerFactory.getLogger(AccessibilityExcelReporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Returns the configured report directory fresh on every call — never frozen. */
    private static Path reportDir() {
        return A11yConfig.outputDir();
    }

    private AccessibilityExcelReporter() {
    }

    /**
     * Reads all scan artifacts from the output directory, builds the workbook,
     * and writes it to disk.
     *
     * @return path to the generated {@code .xlsx} file, or {@code null} on failure
     */
    public static Path generate() {
        Path dir = reportDir();
        try {
            Files.createDirectories(dir);
            List<ScanRecord> scans = loadScanRecords();

            String timestamp = LocalDateTime.now().format(FILE_TS);
            Path outputPath = dir.resolve("accessibility-report_" + timestamp + ".xlsx");

            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                StyleKit styles = new StyleKit(workbook);
                buildSummarySheet(workbook, styles, scans);
                buildScanHistorySheet(workbook, styles, scans);
                buildViolationsDetailSheet(workbook, styles, scans);

                try (OutputStream out = Files.newOutputStream(outputPath)) {
                    workbook.write(out);
                }
            }

            logger.info("Accessibility Excel report generated: {}", outputPath.toAbsolutePath());
            return outputPath;

        } catch (Throwable e) {
            logger.warn("Unable to generate accessibility Excel report: {}", e.getMessage(), e);
            return null;
        }
    }

    private static List<ScanRecord> loadScanRecords() throws IOException {
        if (!Files.exists(reportDir())) {
            return Collections.emptyList();
        }

        // Collect both axe-core scan artifacts (*_a11y.json) AND interaction/structural/
        // WCAG-2.2/motion artifacts (*_interaction_*.json) so that all engine layers are
        // represented in the final report.  Previously only _a11y.json files were read,
        // which caused silent data-loss of Layers 2-5 findings.
        List<Path> files;
        try (Stream<Path> stream = Files.list(reportDir())) {
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
                if (fileName.contains("_interaction_")) {
                    // Interaction / structural / WCAG-2.2 / motion artifact
                    ScanRecord record = parseInteractionArtifact(file);
                    if (record != null) records.add(record);
                } else {
                    // axe-core artifact
                    ScanRecord record = parseAxeArtifact(file);
                    if (record != null) records.add(record);
                }
            } catch (Exception ex) {
                logger.debug("Skipping malformed accessibility artifact {}: {}", file, ex.getMessage());
            }
        }
        return records;
    }

    private static ScanRecord parseAxeArtifact(Path file) {
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            ScanRecord record = new ScanRecord();
            record.timestamp    = root.path("timestamp").asText("");
            record.pageName     = root.path("pageName").asText("(unknown)");
            record.outcome      = root.path("outcome").asText("UNKNOWN");
            record.violationCount = root.path("violationCount").asInt(0);
            record.errorMessage = root.path("error").asText(null);
            record.engine       = "axe-core";

            JsonNode tagsNode = root.path("tags");
            if (tagsNode.isArray()) {
                StreamSupport.stream(tagsNode.spliterator(), false)
                        .map(JsonNode::asText)
                        .filter(s -> !s.trim().isEmpty())
                        .forEach(record.tags::add);
            }

            for (JsonNode v : root.path("violations")) {
                ViolationRecord vr = new ViolationRecord();
                vr.ruleId  = v.path("id").asText("");
                vr.impact  = v.path("impact").asText("UNKNOWN").toUpperCase(Locale.ROOT);
                vr.description     = v.path("description").asText("");
                vr.help    = v.path("help").asText("");
                vr.helpUrl = v.path("helpUrl").asText("");
                vr.wcagCriterion = v.path("wcagCriterion").asText("");
                vr.confidence    = v.path("confidence").asText("");
                JsonNode elements  = v.path("affectedElements");
                vr.affectedElementCount = elements.isArray() ? elements.size() : 0;
                if (elements.isArray() && elements.size() > 0) {
                    vr.firstAffectedElement = elements.get(0).asText("(element)");
                }
                record.violations.add(vr);
            }
            return record;
        } catch (Exception ex) {
            logger.debug("Skipping malformed axe artifact {}: {}", file, ex.getMessage());
            return null;
        }
    }

    /**
     * Parses a Layer 2-5 interaction/structural/WCAG2.2/motion artifact
     * ({@code *_interaction_<checkId>.json}) written by
     * {@link AccessibilityChecker#writeInteractionArtifact}.
     *
     * <p>Maps each {@code issues[]} entry to a {@link ViolationRecord} so the
     * findings appear in the Violations Detail sheet alongside axe-core violations.
     * This closes the data-loss gap where all non-axe findings were previously
     * invisible in the post-run Excel report.</p>
     */
    private static ScanRecord parseInteractionArtifact(Path file) {
        try {
            JsonNode root = MAPPER.readTree(file.toFile());
            ScanRecord record = new ScanRecord();
            record.timestamp  = root.path("timestamp").asText("");
            record.pageName   = root.path("pageName").asText("(unknown)");
            record.outcome    = root.path("outcome").asText("UNKNOWN");
            record.engine     = "Interaction:" + root.path("checkId").asText("custom");
            record.violationCount = root.path("issueCount").asInt(0);

            for (JsonNode issue : root.path("issues")) {
                ViolationRecord vr = new ViolationRecord();
                vr.ruleId       = issue.path("ruleId").asText("");
                vr.impact       = issue.path("impact").asText("MODERATE").toUpperCase(Locale.ROOT);
                vr.description  = issue.path("description").asText("");
                vr.help         = issue.path("wcagRef").asText("");
                vr.helpUrl      = issue.path("helpUrl").asText("");
                vr.wcagCriterion = issue.path("wcagCriterion").asText("");
                vr.confidence    = issue.path("confidence").asText("");
                vr.firstAffectedElement = issue.path("element").asText("(element)");
                vr.affectedElementCount = 1;
                boolean needsReview = issue.path("needsReview").asBoolean(false);
                if (needsReview) {
                    vr.impact = "NEEDS_REVIEW";
                }
                record.violations.add(vr);
            }
            return record;
        } catch (Exception ex) {
            logger.debug("Skipping malformed interaction artifact {}: {}", file, ex.getMessage());
            return null;
        }
    }

    /** Sheet 1 — Summary KPIs and impact distribution. */
    private static void buildSummarySheet(XSSFWorkbook wb, StyleKit sk, List<ScanRecord> scans) {
        XSSFSheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, 32 * 256);
        sheet.setColumnWidth(1, 18 * 256);

        int row = 0;

        row = writeTitle(sheet, sk, row, "Accessibility Test Run Summary",
                "Generated: " + LocalDateTime.now().format(DISPLAY_TS)
                        + "   |   Source: " + reportDir());
        row++;

        row = writeSectionHeader(sheet, sk, row, "Run Overview");

        long pass = countOutcome(scans, "PASS");
        long fail = countOutcome(scans, "FAIL");
        long skipped = countOutcome(scans, "SKIPPED");
        long error = countOutcome(scans, "ERROR");
        int totalViolations = scans.stream().mapToInt(s -> s.violationCount).sum();
        long uniqueRules = scans.stream()
                .flatMap(s -> s.violations.stream().map(v -> v.ruleId))
                .filter(id -> !id.trim().isEmpty())
                .distinct().count();

        row = writeKV(sheet, sk, row, "Total Scans", String.valueOf(scans.size()));
        row = writeKV(sheet, sk, row, "PASS", String.valueOf(pass));
        row = writeKV(sheet, sk, row, "FAIL", String.valueOf(fail));
        row = writeKV(sheet, sk, row, "SKIPPED", String.valueOf(skipped));
        row = writeKV(sheet, sk, row, "ERROR", String.valueOf(error));
        row = writeKV(sheet, sk, row, "Total Violations", String.valueOf(totalViolations));
        row = writeKV(sheet, sk, row, "Unique Rule IDs", String.valueOf(uniqueRules));
        row++;

        row = writeSectionHeader(sheet, sk, row, "Impact Distribution");
        row = writeTableHeader(sheet, sk, row, "Impact", "Count");

        Map<String, Long> impactCounts = new LinkedHashMap<>();
        impactCounts.put("CRITICAL", 0L);
        impactCounts.put("SERIOUS", 0L);
        impactCounts.put("MODERATE", 0L);
        impactCounts.put("MINOR", 0L);
        impactCounts.put("UNKNOWN", 0L);
        scans.forEach(s -> s.violations.forEach(v ->
                impactCounts.merge(v.impact.trim().isEmpty() ? "UNKNOWN" : v.impact, 1L, Long::sum)));

        for (Map.Entry<String, Long> entry : impactCounts.entrySet()) {
            row = writeDataRow(sheet, sk, row, false, entry.getKey(), String.valueOf(entry.getValue()));
        }
        row++;

        row = writeSectionHeader(sheet, sk, row, "Top 10 Rule IDs");
        row = writeTableHeader(sheet, sk, row, "Rule ID", "Occurrences");

        List<Map.Entry<String, Long>> topRules = scans.stream()
                .flatMap(s -> s.violations.stream().map(v -> v.ruleId))
                .filter(id -> !id.trim().isEmpty())
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());

        boolean ruleAlt = false;
        for (Map.Entry<String, Long> entry : topRules) {
            row = writeDataRow(sheet, sk, row, ruleAlt, entry.getKey(), String.valueOf(entry.getValue()));
            ruleAlt = !ruleAlt;
        }
        if (topRules.isEmpty()) {
            writeDataRow(sheet, sk, row, false, "(no violations)", "0");
        }
    }

    /** Sheet 2 — One row per scan. */
    private static void buildScanHistorySheet(XSSFWorkbook wb, StyleKit sk, List<ScanRecord> scans) {
        XSSFSheet sheet = wb.createSheet("Scan History");
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 40 * 256);
        sheet.setColumnWidth(2, 12 * 256);
        sheet.setColumnWidth(3, 14 * 256);
        sheet.setColumnWidth(4, 35 * 256);
        sheet.setColumnWidth(5, 40 * 256);

        int row = 0;
        row = writeTitle(sheet, sk, row, "Scan History", null);
        row++;

        row = writeTableHeader(sheet, sk, row,
                "Timestamp", "Page Name", "Outcome", "Violations", "WCAG Tags", "Error");

        List<ScanRecord> sorted = scans.stream()
                .sorted(Comparator.comparing((ScanRecord s) -> s.timestamp).reversed())
                .collect(Collectors.toList());

        boolean alt = false;
        for (ScanRecord scan : sorted) {
            Row r = sheet.createRow(row++);
            applyDataStyle(r.createCell(0), sk, alt).setCellValue(scan.timestamp);
            applyDataStyle(r.createCell(1), sk, alt).setCellValue(scan.pageName);
            Cell outcomeCell = applyDataStyle(r.createCell(2), sk, alt);
            outcomeCell.setCellValue(scan.outcome);
            applyOutcomeStyle(outcomeCell, scan.outcome, wb);
            applyDataStyle(r.createCell(3), sk, alt).setCellValue(scan.violationCount);
            applyDataStyle(r.createCell(4), sk, alt).setCellValue(String.join(", ", scan.tags));
            applyDataStyle(r.createCell(5), sk, alt).setCellValue(scan.errorMessage != null ? scan.errorMessage : "");
            alt = !alt;
        }

        if (sorted.isEmpty()) {
            Row r = sheet.createRow(row);
            r.createCell(0).setCellValue("No accessibility scan artifacts found for this run.");
        }
    }

    /** Sheet 3 — One row per individual violation. */
    private static void buildViolationsDetailSheet(XSSFWorkbook wb, StyleKit sk, List<ScanRecord> scans) {
        XSSFSheet sheet = wb.createSheet("Violations Detail");
        sheet.setColumnWidth(0, 22 * 256);
        sheet.setColumnWidth(1, 40 * 256);
        sheet.setColumnWidth(2, 10 * 256);
        sheet.setColumnWidth(3, 20 * 256);
        sheet.setColumnWidth(4, 12 * 256);
        sheet.setColumnWidth(5, 50 * 256);
        sheet.setColumnWidth(6, 50 * 256);
        sheet.setColumnWidth(7, 60 * 256);
        sheet.setColumnWidth(8, 14 * 256);
        sheet.setColumnWidth(9, 80 * 256);
        sheet.setColumnWidth(10, 14 * 256);
        sheet.setColumnWidth(11, 16 * 256);

        int row = 0;
        row = writeTitle(sheet, sk, row, "Violations Detail", null);
        row++;

        row = writeTableHeader(sheet, sk, row,
                "Timestamp", "Page Name", "Outcome",
                "Rule ID", "Impact", "Description", "Help",
                "Help URL", "Affected Elements", "First Element (HTML)",
                "WCAG SC", "Confidence");

        boolean alt = false;
        for (ScanRecord scan : scans) {
            for (ViolationRecord vr : scan.violations) {
                Row r = sheet.createRow(row++);
                applyDataStyle(r.createCell(0), sk, alt).setCellValue(scan.timestamp);
                applyDataStyle(r.createCell(1), sk, alt).setCellValue(scan.pageName);
                applyDataStyle(r.createCell(2), sk, alt).setCellValue(scan.outcome);
                applyDataStyle(r.createCell(3), sk, alt).setCellValue(vr.ruleId);
                Cell impactCell = applyDataStyle(r.createCell(4), sk, alt);
                impactCell.setCellValue(vr.impact);
                applyImpactStyle(impactCell, vr.impact, wb);
                applyDataStyle(r.createCell(5), sk, alt).setCellValue(vr.description);
                applyDataStyle(r.createCell(6), sk, alt).setCellValue(vr.help);
                applyDataStyle(r.createCell(7), sk, alt).setCellValue(vr.helpUrl);
                applyDataStyle(r.createCell(8), sk, alt).setCellValue(vr.affectedElementCount);
                applyDataStyle(r.createCell(9), sk, alt).setCellValue(
                        vr.firstAffectedElement != null ? vr.firstAffectedElement : "");
                applyDataStyle(r.createCell(10), sk, alt).setCellValue(vr.wcagCriterion);
                applyDataStyle(r.createCell(11), sk, alt).setCellValue(vr.confidence);
                alt = !alt;
            }
        }

        if (scans.stream().allMatch(s -> s.violations.isEmpty())) {
            Row r = sheet.createRow(row);
            r.createCell(0).setCellValue("No violations found in any scan.");
        }
    }

    private static int writeTitle(Sheet sheet, StyleKit sk, int rowIdx, String title, String subtitle) {
        Row r = sheet.createRow(rowIdx++);
        r.setHeightInPoints(28);
        Cell c = r.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(sk.title);
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            Row sub = sheet.createRow(rowIdx++);
            Cell sc = sub.createCell(0);
            sc.setCellValue(subtitle);
            sc.setCellStyle(sk.muted);
        }
        return rowIdx;
    }

    private static int writeSectionHeader(Sheet sheet, StyleKit sk, int rowIdx, String header) {
        Row r = sheet.createRow(rowIdx++);
        r.setHeightInPoints(18);
        Cell c = r.createCell(0);
        c.setCellValue(header);
        c.setCellStyle(sk.sectionHeader);
        return rowIdx;
    }

    private static int writeKV(Sheet sheet, StyleKit sk, int rowIdx, String key, String value) {
        Row r = sheet.createRow(rowIdx++);
        Cell k = r.createCell(0);
        k.setCellValue(key);
        k.setCellStyle(sk.kv_key);
        Cell v = r.createCell(1);
        v.setCellValue(value);
        v.setCellStyle(sk.kv_value);
        return rowIdx;
    }

    private static int writeTableHeader(Sheet sheet, StyleKit sk, int rowIdx, String... headers) {
        Row r = sheet.createRow(rowIdx++);
        r.setHeightInPoints(16);
        for (int i = 0; i < headers.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(sk.tableHeader);
        }
        return rowIdx;
    }

    private static int writeDataRow(Sheet sheet, StyleKit sk, int rowIdx, boolean alt, String col1, String col2) {
        Row r = sheet.createRow(rowIdx++);
        Cell c1 = r.createCell(0);
        c1.setCellValue(col1);
        c1.setCellStyle(alt ? sk.dataAlt : sk.data);
        Cell c2 = r.createCell(1);
        c2.setCellValue(col2);
        c2.setCellStyle(alt ? sk.dataAlt : sk.data);
        return rowIdx;
    }

    private static Cell applyDataStyle(Cell c, StyleKit sk, boolean alt) {
        c.setCellStyle(alt ? sk.dataAlt : sk.data);
        return c;
    }

    private static void applyOutcomeStyle(Cell c, String outcome, XSSFWorkbook wb) {
        if (outcome == null) return;
        XSSFCellStyle style = wb.createCellStyle();
        style.cloneStyleFrom(c.getCellStyle());
        XSSFFont font = wb.createFont();
        font.setBold(true);
        switch (outcome.toUpperCase(Locale.ROOT)) {
            case "PASS":
                font.setColor(new XSSFColor(new byte[]{(byte) 0x27, (byte) 0x7D, (byte) 0x3B}, null));
                break;
            case "FAIL":
                font.setColor(new XSSFColor(new byte[]{(byte) 0xC0, (byte) 0x39, (byte) 0x2B}, null));
                break;
            case "SKIPPED":
                font.setColor(new XSSFColor(new byte[]{(byte) 0xF3, (byte) 0x9C, (byte) 0x12}, null));
                break;
            case "ERROR":
                font.setColor(new XSSFColor(new byte[]{(byte) 0x8E, (byte) 0x44, (byte) 0xAD}, null));
                break;
            default:
                return;
        }
        style.setFont(font);
        c.setCellStyle(style);
    }

    private static void applyImpactStyle(Cell c, String impact, XSSFWorkbook wb) {
        if (impact == null) return;
        XSSFCellStyle style = wb.createCellStyle();
        style.cloneStyleFrom(c.getCellStyle());
        XSSFFont font = wb.createFont();
        font.setBold(true);
        switch (impact.toUpperCase(Locale.ROOT)) {
            case "CRITICAL":
                font.setColor(new XSSFColor(new byte[]{(byte) 0xC0, (byte) 0x39, (byte) 0x2B}, null));
                break;
            case "SERIOUS":
                font.setColor(new XSSFColor(new byte[]{(byte) 0xE6, (byte) 0x7E, (byte) 0x22}, null));
                break;
            case "MODERATE":
                font.setColor(new XSSFColor(new byte[]{(byte) 0xF3, (byte) 0x9C, (byte) 0x12}, null));
                break;
            case "MINOR":
                font.setColor(new XSSFColor(new byte[]{(byte) 0x27, (byte) 0x7D, (byte) 0x3B}, null));
                break;
            default:
                return;
        }
        style.setFont(font);
        c.setCellStyle(style);
    }

    private static final class StyleKit {
        final XSSFCellStyle title;
        final XSSFCellStyle muted;
        final XSSFCellStyle sectionHeader;
        final XSSFCellStyle kv_key;
        final XSSFCellStyle kv_value;
        final XSSFCellStyle tableHeader;
        final XSSFCellStyle data;
        final XSSFCellStyle dataAlt;

        StyleKit(XSSFWorkbook wb) {
            XSSFFont titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(new XSSFColor(new byte[]{(byte) 0x1A, (byte) 0x53, (byte) 0x76}, null));

            XSSFFont mutedFont = wb.createFont();
            mutedFont.setFontHeightInPoints((short) 9);
            mutedFont.setColor(new XSSFColor(new byte[]{(byte) 0x88, (byte) 0x88, (byte) 0x88}, null));

            XSSFFont sectionFont = wb.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 11);
            sectionFont.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));

            XSSFFont kvKeyFont = wb.createFont();
            kvKeyFont.setBold(true);
            kvKeyFont.setFontHeightInPoints((short) 10);

            XSSFFont dataFont = wb.createFont();
            dataFont.setFontHeightInPoints((short) 10);

            title = wb.createCellStyle();
            title.setFont(titleFont);

            muted = wb.createCellStyle();
            muted.setFont(mutedFont);

            sectionHeader = wb.createCellStyle();
            sectionHeader.setFont(sectionFont);
            sectionHeader.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x1A, (byte) 0x53, (byte) 0x76}, null));
            sectionHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            kv_key = wb.createCellStyle();
            kv_key.setFont(kvKeyFont);
            kv_key.setBorderBottom(BorderStyle.THIN);
            kv_key.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            kv_value = wb.createCellStyle();
            kv_value.setFont(dataFont);
            kv_value.setBorderBottom(BorderStyle.THIN);
            kv_value.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            tableHeader = wb.createCellStyle();
            XSSFFont thFont = wb.createFont();
            thFont.setBold(true);
            thFont.setFontHeightInPoints((short) 10);
            thFont.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
            tableHeader.setFont(thFont);
            tableHeader.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x2E, (byte) 0x75, (byte) 0xB6}, null));
            tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tableHeader.setBorderBottom(BorderStyle.THIN);
            tableHeader.setBottomBorderColor(IndexedColors.WHITE.getIndex());
            tableHeader.setWrapText(false);

            data = wb.createCellStyle();
            data.setFont(dataFont);
            data.setBorderBottom(BorderStyle.THIN);
            data.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            data.setWrapText(false);

            dataAlt = wb.createCellStyle();
            dataAlt.setFont(dataFont);
            dataAlt.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xF2, (byte) 0xF7, (byte) 0xFD}, null));
            dataAlt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            dataAlt.setBorderBottom(BorderStyle.THIN);
            dataAlt.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            dataAlt.setWrapText(false);
        }
    }

    private static long countOutcome(List<ScanRecord> scans, String outcome) {
        return scans.stream().filter(s -> outcome.equalsIgnoreCase(s.outcome)).count();
    }

    private static final class ScanRecord {
        String timestamp = "";
        String pageName = "";
        String outcome = "";
        int violationCount = 0;
        String errorMessage = null;
        /** Engine that produced this record: "axe-core" or "Interaction:<checkId>". */
        String engine = "axe-core";
        final List<String> tags = new ArrayList<>();
        final List<ViolationRecord> violations = new ArrayList<>();
    }

    private static final class ViolationRecord {
        String ruleId = "";
        String impact = "";
        String description = "";
        String help = "";
        String helpUrl = "";
        int affectedElementCount = 0;
        String firstAffectedElement = null;
        /** WCAG success criterion, e.g. "1.4.3", or "" when not derivable. Empty for artifacts written before this field existed. */
        String wcagCriterion = "";
        /** "violation" | "manual_review", or "" for artifacts written before this field existed. */
        String confidence = "";
    }
}

