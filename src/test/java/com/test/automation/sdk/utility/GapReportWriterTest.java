package com.test.automation.sdk.utility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GapReportWriter.
 *
 * Validates three-tier directory resolution:
 *   1. -Dsdk.gapOutputDir system property
 *   2. sdk-config.yaml reporting.gapOutputDir
 *   3. Built-in default: docs/test-case-gaps/
 */
@DisplayName("GapReportWriter -- output directory resolution and file writing")
class GapReportWriterTest {

    @TempDir
    File tempDir;

    @BeforeEach
    void resetSingletonAndProp() throws Exception {
        System.clearProperty(GapReportWriter.SYSTEM_PROP);
        System.clearProperty("sdk.config.dir");
        resetYamlSingleton();
    }

    @AfterEach
    void cleanupProp() throws Exception {
        System.clearProperty(GapReportWriter.SYSTEM_PROP);
        System.clearProperty("sdk.config.dir");
        resetYamlSingleton();
    }

    // ---------------------------------------------------------------
    // Directory resolution
    // ---------------------------------------------------------------

    @Test
    @DisplayName("resolveGapOutputDir -- returns default when no config and no -D prop")
    void resolveDir_defaultWhenNothingSet() {
        // Point yaml reader to an empty dir so no yaml loads
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        String dir = GapReportWriter.resolveGapOutputDir();
        assertEquals("docs/test-case-gaps/", dir);
    }

    @Test
    @DisplayName("resolveGapOutputDir -- -Dsdk.gapOutputDir overrides default")
    void resolveDir_systemPropOverridesDefault() {
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        System.setProperty(GapReportWriter.SYSTEM_PROP, "my/custom/gaps");
        String dir = GapReportWriter.resolveGapOutputDir();
        assertEquals("my/custom/gaps/", dir);
    }

    @Test
    @DisplayName("resolveGapOutputDir -- trailing separator appended when missing")
    void resolveDir_trailingSeparatorAppended() {
        System.setProperty(GapReportWriter.SYSTEM_PROP, "some/path");
        assertTrue(GapReportWriter.resolveGapOutputDir().endsWith("/"));
    }

    @Test
    @DisplayName("resolveGapOutputDir -- trailing separator not doubled when already present")
    void resolveDir_trailingSeparatorNotDoubled() {
        System.setProperty(GapReportWriter.SYSTEM_PROP, "some/path/");
        assertEquals("some/path/", GapReportWriter.resolveGapOutputDir());
    }

    @Test
    @DisplayName("resolveGapOutputDir -- yaml reporting.gapOutputDir overrides default")
    void resolveDir_yamlOverridesDefault() throws Exception {
        File yamlFile = new File(tempDir, "sdk-config.yaml");
        writeFile(yamlFile,
                "reporting:\n" +
                "  gapOutputDir: \"custom/gap/output/\"\n");
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        resetYamlSingleton();

        String dir = GapReportWriter.resolveGapOutputDir();
        assertEquals("custom/gap/output/", dir);
    }

    @Test
    @DisplayName("resolveGapOutputDir -- -D prop takes priority over yaml")
    void resolveDir_systemPropTakesPriorityOverYaml() throws Exception {
        File yamlFile = new File(tempDir, "sdk-config.yaml");
        writeFile(yamlFile,
                "reporting:\n" +
                "  gapOutputDir: \"yaml/path/\"\n");
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        resetYamlSingleton();

        System.setProperty(GapReportWriter.SYSTEM_PROP, "prop/path");
        String dir = GapReportWriter.resolveGapOutputDir();
        assertEquals("prop/path/", dir);
    }

    // ---------------------------------------------------------------
    // File writing
    // ---------------------------------------------------------------

    @Test
    @DisplayName("writeGapReport -- creates file with ADO-<ID>-gap-report.md naming for numeric ID")
    void writeGapReport_numericIdUsesAdoPrefix() throws Exception {
        String outDir = new File(tempDir, "gaps").getAbsolutePath();
        System.setProperty(GapReportWriter.SYSTEM_PROP, outDir);

        String path = GapReportWriter.writeGapReport("12345", "# Gap content");
        assertNotNull(path);
        assertTrue(new File(path).getName().equals("ADO-12345-gap-report.md"));
    }

    @Test
    @DisplayName("writeGapReport -- uses <ClassName>-gap-report.md naming for non-numeric identifier")
    void writeGapReport_classNameIdentifier() throws Exception {
        String outDir = new File(tempDir, "gaps").getAbsolutePath();
        System.setProperty(GapReportWriter.SYSTEM_PROP, outDir);

        String path = GapReportWriter.writeGapReport("Test_Login", "# Gap content");
        assertNotNull(path);
        assertTrue(new File(path).getName().equals("Test_Login-gap-report.md"));
    }

    @Test
    @DisplayName("writeBlockerReport -- creates file with ADO-<ID>-blocker-report.md naming for numeric ID")
    void writeBlockerReport_numericIdUsesAdoPrefix() throws Exception {
        String outDir = new File(tempDir, "blockers").getAbsolutePath();
        System.setProperty(GapReportWriter.SYSTEM_PROP, outDir);

        String path = GapReportWriter.writeBlockerReport("99999", "# Blocker content");
        assertNotNull(path);
        assertTrue(new File(path).getName().equals("ADO-99999-blocker-report.md"));
    }

    @Test
    @DisplayName("writeBlockerReport -- uses <ClassName>-blocker-report.md naming for non-numeric identifier")
    void writeBlockerReport_classNameIdentifier() throws Exception {
        String outDir = new File(tempDir, "blockers").getAbsolutePath();
        System.setProperty(GapReportWriter.SYSTEM_PROP, outDir);

        String path = GapReportWriter.writeBlockerReport("Test_ExportPdf", "# Blocker content");
        assertNotNull(path);
        assertEquals("Test_ExportPdf-blocker-report.md", new File(path).getName());
    }

    @Test
    @DisplayName("writeGapReport -- file content matches what was passed in")
    void writeGapReport_contentWrittenCorrectly() throws Exception {
        String outDir = new File(tempDir, "out").getAbsolutePath();
        System.setProperty(GapReportWriter.SYSTEM_PROP, outDir);

        String content = "# My Gap\nSome details here";
        String path = GapReportWriter.writeGapReport("Test_MyFeature", content);
        assertNotNull(path);
        String written = new String(java.nio.file.Files.readAllBytes(new File(path).toPath()), "UTF-8");
        assertEquals(content, written);
    }

    @Test
    @DisplayName("writeGapReport -- output directory is created automatically")
    void writeGapReport_createsDirectoryIfAbsent() throws Exception {
        File nestedDir = new File(tempDir, "a/b/c/gaps");
        System.setProperty(GapReportWriter.SYSTEM_PROP, nestedDir.getAbsolutePath());

        String path = GapReportWriter.writeGapReport("ADO-777", "content");
        assertNotNull(path);
        assertTrue(nestedDir.exists());
    }

    @Test
    @DisplayName("today -- returns non-null formatted date string")
    void today_returnsDateString() {
        String date = GapReportWriter.today();
        assertNotNull(date);
        assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    // ---------------------------------------------------------------
    // YamlConfigReader default key
    // ---------------------------------------------------------------

    @Test
    @DisplayName("YamlConfigReader defaults include reporting.gapOutputDir")
    void yamlDefaultIncludesGapOutputDir() throws Exception {
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        resetYamlSingleton();
        String val = YamlConfigReader.get("reporting.gapOutputDir");
        // Raw yaml value has no trailing slash; GapReportWriter.resolveGapOutputDir() normalises it
        assertEquals("docs/test-case-gaps", val);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static void resetYamlSingleton() throws Exception {
        Field f = YamlConfigReader.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static void writeFile(File f, String content) throws Exception {
        java.io.FileWriter fw = new java.io.FileWriter(f);
        try { fw.write(content); } finally { fw.close(); }
    }
}
