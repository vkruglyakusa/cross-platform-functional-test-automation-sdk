package com.test.automation.sdk.utility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PageObjectGenerator project-agnostic configuration.
 *
 * Validates that the three output-path values (uiActionsPackage, outputSrc,
 * outputReport) resolve correctly from:
 *   1. -D system properties  (-Dpog.package / -Dpog.outputDir / -Dpog.reportDir)
 *   2. sdk-config.yaml       (crawler.pageObject.package / outputDir / reportDir)
 *   3. Built-in defaults     (backward-compatible Poletop paths)
 *
 * Does NOT require a browser or network connection -- tests invoke the static
 * resolve*() helpers via reflection so no WebDriver is needed.
 */
@DisplayName("PageObjectGenerator -- project-agnostic output path resolution")
class PageObjectGeneratorConfigTest {

    @TempDir
    File tempDir;

    private String savedConfigDir;
    private String savedPogPackage;
    private String savedPogOutputDir;
    private String savedPogReportDir;

    @BeforeEach
    void setup() {
        savedConfigDir   = System.getProperty("sdk.config.dir");
        savedPogPackage  = System.getProperty("pog.package");
        savedPogOutputDir = System.getProperty("pog.outputDir");
        savedPogReportDir = System.getProperty("pog.reportDir");
        clearPogProperties();
        resetYamlSingleton();
    }

    @AfterEach
    void teardown() {
        clearPogProperties();
        restoreProperty("sdk.config.dir",  savedConfigDir);
        restoreProperty("pog.package",     savedPogPackage);
        restoreProperty("pog.outputDir",   savedPogOutputDir);
        restoreProperty("pog.reportDir",   savedPogReportDir);
        resetYamlSingleton();
    }

    // -- Built-in defaults (no YAML, no -D props) ------------------------------

    @Test
    @DisplayName("resolveUiActionsPackage() returns non-null default when no YAML and no -D property")
    void defaults_uiActionsPackage_isNonNull() throws Exception {
        pointToEmptyDir();
        String pkg = invokeResolvePackage();
        assertNotNull(pkg, "default package must not be null");
        assertFalse(pkg.isEmpty(), "default package must not be empty");
        assertTrue(pkg.contains("."), "default package must be a valid Java package");
    }

    @Test
    @DisplayName("resolveOutputSrc() returns non-null default when no YAML and no -D property")
    void defaults_outputSrc_isNonNull() throws Exception {
        pointToEmptyDir();
        String src = invokeResolveOutputSrc();
        assertNotNull(src, "default outputSrc must not be null");
        assertFalse(src.isEmpty(), "default outputSrc must not be empty");
    }

    @Test
    @DisplayName("resolveOutputSrc() default ends with a path separator")
    void defaults_outputSrc_endsWithSeparator() throws Exception {
        pointToEmptyDir();
        String src = invokeResolveOutputSrc();
        assertTrue(src.endsWith("/") || src.endsWith("\\"),
            "outputSrc should end with a path separator, was: " + src);
    }

    @Test
    @DisplayName("resolveOutputReport() default is 'test-output/crawler/'")
    void defaults_outputReport_isTestOutputCrawler() throws Exception {
        pointToEmptyDir();
        String report = invokeResolveOutputReport();
        assertEquals("test-output/crawler/", report);
    }

    // -- YAML file overrides ----------------------------------------------------

    @Test
    @DisplayName("resolveUiActionsPackage() returns value from sdk-config.yaml")
    void yaml_overrides_package() throws Exception {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    package: com.acme.qa.uiActions\n"
        );
        assertEquals("com.acme.qa.uiActions", invokeResolvePackage());
    }

    @Test
    @DisplayName("resolveOutputSrc() returns value from sdk-config.yaml")
    void yaml_overrides_outputSrc() throws Exception {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    outputDir: src/main/java/com/acme/qa/uiActions/\n"
        );
        assertEquals("src/main/java/com/acme/qa/uiActions/", invokeResolveOutputSrc());
    }

    @Test
    @DisplayName("resolveOutputReport() returns value from sdk-config.yaml")
    void yaml_overrides_reportDir() throws Exception {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    reportDir: target/crawler-reports/\n"
        );
        assertEquals("target/crawler-reports/", invokeResolveOutputReport());
    }

    @Test
    @DisplayName("All three values resolved from sdk-config.yaml in one block")
    void yaml_overrides_allThreeValues() throws Exception {
        writeYaml(
            "crawler:\n" +
            "  pageObject:\n" +
            "    package: com.example.automation.uiActions\n" +
            "    outputDir: src/main/java/com/example/automation/uiActions/\n" +
            "    reportDir: output/crawler/\n"
        );
        assertEquals("com.example.automation.uiActions",               invokeResolvePackage());
        assertEquals("src/main/java/com/example/automation/uiActions/", invokeResolveOutputSrc());
        assertEquals("output/crawler/",                                  invokeResolveOutputReport());
    }

    // -- -D system property overrides (highest priority) -----------------------

    @Test
    @DisplayName("-Dpog.package overrides sdk-config.yaml value")
    void systemProperty_overrides_yaml_package() throws Exception {
        writeYaml("crawler:\n  pageObject:\n    package: com.yaml.package\n");
        System.setProperty("pog.package", "com.sysprop.override");
        assertEquals("com.sysprop.override", invokeResolvePackage());
    }

    @Test
    @DisplayName("-Dpog.outputDir overrides sdk-config.yaml value")
    void systemProperty_overrides_yaml_outputDir() throws Exception {
        writeYaml("crawler:\n  pageObject:\n    outputDir: src/yaml/path/\n");
        System.setProperty("pog.outputDir", "src/sysprop/path/");
        assertEquals("src/sysprop/path/", invokeResolveOutputSrc());
    }

    @Test
    @DisplayName("-Dpog.reportDir overrides sdk-config.yaml value")
    void systemProperty_overrides_yaml_reportDir() throws Exception {
        writeYaml("crawler:\n  pageObject:\n    reportDir: yaml-reports/\n");
        System.setProperty("pog.reportDir", "sysprop-reports/");
        assertEquals("sysprop-reports/", invokeResolveOutputReport());
    }

    @Test
    @DisplayName("-Dpog.package overrides built-in default when no YAML")
    void systemProperty_overrides_default_package() throws Exception {
        pointToEmptyDir();
        System.setProperty("pog.package", "com.override.uiActions");
        assertEquals("com.override.uiActions", invokeResolvePackage());
    }

    @Test
    @DisplayName("-Dpog.outputDir without trailing slash gets a separator appended")
    void systemProperty_outputDir_withoutSeparator_getsSlashAppended() throws Exception {
        pointToEmptyDir();
        System.setProperty("pog.outputDir", "src/main/java/com/override/uiActions");
        String result = invokeResolveOutputSrc();
        assertTrue(result.endsWith("/") || result.endsWith("\\"),
            "outputDir from -D property should end with separator, was: " + result);
    }

    @Test
    @DisplayName("-Dpog.outputDir with trailing slash is returned unchanged")
    void systemProperty_outputDir_withSeparator_unchangedAfterRead() throws Exception {
        pointToEmptyDir();
        System.setProperty("pog.outputDir", "src/main/java/com/override/uiActions/");
        assertEquals("src/main/java/com/override/uiActions/", invokeResolveOutputSrc());
    }

    // -- YAML takes precedence over default but not over -D --------------------

    @Test
    @DisplayName("YAML value is used when -D property is absent, not the built-in default")
    void yaml_takesOver_default_when_sysprop_absent() throws Exception {
        writeYaml("crawler:\n  pageObject:\n    package: com.yaml.overridepackage\n");
        // No pog.package system property set
        String pkg = invokeResolvePackage();
        assertEquals("com.yaml.overridepackage", pkg,
            "YAML value should override the built-in default");
    }

    // -- Helper methods --------------------------------------------------------

    /** Invoke the private static PageObjectGenerator.resolveUiActionsPackage() */
    private String invokeResolvePackage() throws Exception {
        return invokeStatic("resolveUiActionsPackage");
    }

    /** Invoke the private static PageObjectGenerator.resolveOutputSrc() */
    private String invokeResolveOutputSrc() throws Exception {
        return invokeStatic("resolveOutputSrc");
    }

    /** Invoke the private static PageObjectGenerator.resolveOutputReport() */
    private String invokeResolveOutputReport() throws Exception {
        return invokeStatic("resolveOutputReport");
    }

    private String invokeStatic(String methodName) throws Exception {
        Method m = PageObjectGenerator.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (String) m.invoke(null);
    }

    private void pointToEmptyDir() {
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        resetYamlSingleton();
    }

    private void writeYaml(String content) throws IOException {
        File yaml = new File(tempDir, "sdk-config.yaml");
        FileWriter fw = new FileWriter(yaml);
        try {
            fw.write(content);
        } finally {
            fw.close();
        }
        System.setProperty("sdk.config.dir", tempDir.getAbsolutePath());
        resetYamlSingleton();
    }

    private void clearPogProperties() {
        System.clearProperty("pog.package");
        System.clearProperty("pog.outputDir");
        System.clearProperty("pog.reportDir");
    }

    private void restoreProperty(String key, String savedValue) {
        if (savedValue != null) {
            System.setProperty(key, savedValue);
        } else {
            System.clearProperty(key);
        }
    }

    private static void resetYamlSingleton() {
        try {
            Field field = com.test.automation.sdk.config.YamlConfigReader.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Could not reset YamlConfigReader singleton", e);
        }
    }
}
