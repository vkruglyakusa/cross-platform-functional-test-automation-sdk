package com.test.automation.sdk.utility;

import com.test.automation.sdk.config.YamlConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Writes gap-report.md and blocker-report.md files to the configured output directory.
 *
 * Output directory resolution order (highest priority first):
 *   1. -Dsdk.gapOutputDir system property
 *   2. sdk-config.yaml  reporting.gapOutputDir
 *   3. Default: docs/test-case-gaps/
 *
 * Usage (from Copilot prompts / test generation):
 *   GapReportWriter.writeGapReport("ADO-12345", "Test_Login", content);
 *   GapReportWriter.writeBlockerReport("Test_ExportPdf", content);
 */
public final class GapReportWriter {

    private static final Logger log = LogManager.getLogger(GapReportWriter.class.getName());

    /** Default directory used when no config or system property is present. */
    public static final String DEFAULT_GAP_OUTPUT_DIR = "docs/test-case-gaps";

    /** System property that overrides the yaml config value at runtime. */
    public static final String SYSTEM_PROP = "sdk.gapOutputDir";

    private GapReportWriter() {}

    /**
     * Resolves the gap output directory using the three-tier priority chain.
     * Returned path always ends with a path separator.
     */
    public static String resolveGapOutputDir() {
        String dir = System.getProperty(SYSTEM_PROP);
        if (dir == null || dir.trim().isEmpty()) {
            dir = YamlConfigReader.get("reporting.gapOutputDir");
        }
        if (dir == null || dir.trim().isEmpty()) {
            dir = DEFAULT_GAP_OUTPUT_DIR;
        }
        dir = dir.trim();
        if (!dir.endsWith("/") && !dir.endsWith("\\")) {
            dir = dir + "/";
        }
        return dir;
    }

    /**
     * Writes a gap report file.
     * File name: ADO-{adoId}-gap-report.md  OR  {className}-gap-report.md
     *
     * @param identifier ADO ID (e.g. "12345") or test class name (e.g. "Test_Login")
     * @param content    Markdown body of the gap report
     * @return absolute path of the written file, or null on failure
     */
    public static String writeGapReport(String identifier, String content) {
        String prefix = identifier.matches("\\d+") ? "ADO-" + identifier : identifier;
        return writeReport(prefix + "-gap-report.md", content);
    }

    /**
     * Writes a blocker report file.
     * File name: ADO-{adoId}-blocker-report.md  OR  {className}-blocker-report.md
     *
     * @param identifier ADO ID (e.g. "12345") or test class name (e.g. "Test_ExportPdf")
     * @param content    Markdown body of the blocker report
     * @return absolute path of the written file, or null on failure
     */
    public static String writeBlockerReport(String identifier, String content) {
        String prefix = identifier.matches("\\d+") ? "ADO-" + identifier : identifier;
        return writeReport(prefix + "-blocker-report.md", content);
    }

    /**
     * Low-level writer -- creates the directory if needed and writes the file.
     */
    private static String writeReport(String fileName, String content) {
        String dir = resolveGapOutputDir();
        File outDir = new File(dir);
        if (!outDir.exists()) {
            boolean created = outDir.mkdirs();
            if (!created && !outDir.exists()) {
                log.error("[GapReportWriter] Could not create output directory: {}", dir);
                return null;
            }
        }
        File outFile = new File(outDir, fileName);
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8");
            writer.write(content);
            log.info("[GapReportWriter] Written: {}", outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
        } catch (IOException e) {
            log.error("[GapReportWriter] Failed to write {}: {}", outFile.getAbsolutePath(), e.getMessage());
            return null;
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
        }
    }

    /** Returns a formatted timestamp suitable for use in report headers: yyyy-MM-dd. */
    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }
}
