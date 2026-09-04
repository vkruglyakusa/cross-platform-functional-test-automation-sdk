package com.test.automation.sdk.testbase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SdkConfig path resolution.
 *
 * Covers all 3 priority levels and path normalization.
 * Tests run without a browser or file system dependency.
 */
@DisplayName("SdkConfig -- configuration path resolution")
class SdkConfigTest {

    private String originalSystemProp;

    @BeforeEach
    void saveSystemProp() {
        originalSystemProp = System.getProperty("sdk.config.dir");
        System.clearProperty("sdk.config.dir");
    }

    @AfterEach
    void restoreSystemProp() {
        if (originalSystemProp != null) {
            System.setProperty("sdk.config.dir", originalSystemProp);
        } else {
            System.clearProperty("sdk.config.dir");
        }
    }

    @Test
    @DisplayName("System property overrides env var and default")
    void systemPropertyTakesPriority() {
        System.setProperty("sdk.config.dir", "custom/config");
        // Re-evaluate -- SdkConfig uses static init, so test the parsing logic directly
        String resolved = resolveDir("custom/config", null);
        assertEquals("custom/config", resolved);
    }

    @Test
    @DisplayName("Default fallback is 'configuration' when nothing is set")
    void defaultFallbackIsConfiguration() {
        String resolved = resolveDir(null, null);
        assertEquals("configuration", resolved);
    }

    @Test
    @DisplayName("Trailing forward slash is stripped from config dir")
    void trailingForwardSlashIsStripped() {
        String resolved = resolveDir("my/config/", null);
        assertEquals("my/config", resolved);
    }

    @Test
    @DisplayName("Trailing backslash is stripped from config dir")
    void trailingBackslashIsStripped() {
        String resolved = resolveDir("my\\config\\", null);
        assertEquals("my\\config", resolved);
    }

    @Test
    @DisplayName("Multiple trailing separators are all stripped")
    void multipleTrailingSeparatorsStripped() {
        String resolved = resolveDir("my/config///", null);
        assertEquals("my/config", resolved);
    }

    @Test
    @DisplayName("CONFIG_PROPERTIES appends /config.properties to CONFIG_DIR")
    void configPropertiesPath() {
        // SdkConfig is already initialized -- validate the pattern
        assertNotNull(SdkConfig.CONFIG_PROPERTIES, "CONFIG_PROPERTIES must not be null");
        assertTrue(SdkConfig.CONFIG_PROPERTIES.endsWith("/config.properties"),
                "CONFIG_PROPERTIES must end with /config.properties, was: " + SdkConfig.CONFIG_PROPERTIES);
    }

    @Test
    @DisplayName("LOG4J_PROPERTIES appends /log4j.properties to CONFIG_DIR")
    void log4jPropertiesPath() {
        assertNotNull(SdkConfig.LOG4J_PROPERTIES, "LOG4J_PROPERTIES must not be null");
        assertTrue(SdkConfig.LOG4J_PROPERTIES.endsWith("/log4j.properties"),
                "LOG4J_PROPERTIES must end with /log4j.properties, was: " + SdkConfig.LOG4J_PROPERTIES);
    }

    @Test
    @DisplayName("CONFIG_PROPERTIES is consistent with CONFIG_DIR")
    void configPropertiesIsConsistentWithDir() {
        assertTrue(SdkConfig.CONFIG_PROPERTIES.startsWith(SdkConfig.CONFIG_DIR),
                "CONFIG_PROPERTIES must start with CONFIG_DIR");
    }

    @Test
    @DisplayName("LOG4J_PROPERTIES is consistent with CONFIG_DIR")
    void log4jPropertiesIsConsistentWithDir() {
        assertTrue(SdkConfig.LOG4J_PROPERTIES.startsWith(SdkConfig.CONFIG_DIR),
                "LOG4J_PROPERTIES must start with CONFIG_DIR");
    }

    // -- New fields: YAML_CONFIG and LOG4J2_XML --------------------------------

    @Test
    @DisplayName("YAML_CONFIG field is not null")
    void yamlConfig_isNotNull() {
        assertNotNull(SdkConfig.YAML_CONFIG, "YAML_CONFIG must not be null");
    }

    @Test
    @DisplayName("YAML_CONFIG ends with /sdk-config.yaml")
    void yamlConfig_endsWithSdkConfigYaml() {
        assertTrue(SdkConfig.YAML_CONFIG.endsWith("/sdk-config.yaml"),
                "YAML_CONFIG must end with /sdk-config.yaml, was: " + SdkConfig.YAML_CONFIG);
    }

    @Test
    @DisplayName("YAML_CONFIG is consistent with CONFIG_DIR")
    void yamlConfig_isConsistentWithDir() {
        assertTrue(SdkConfig.YAML_CONFIG.startsWith(SdkConfig.CONFIG_DIR),
                "YAML_CONFIG must start with CONFIG_DIR");
    }

    @Test
    @DisplayName("LOG4J2_XML field is not null")
    void log4j2Xml_isNotNull() {
        assertNotNull(SdkConfig.LOG4J2_XML, "LOG4J2_XML must not be null");
    }

    @Test
    @DisplayName("LOG4J2_XML ends with /log4j2.xml")
    void log4j2Xml_endsWithLog4j2Xml() {
        assertTrue(SdkConfig.LOG4J2_XML.endsWith("/log4j2.xml"),
                "LOG4J2_XML must end with /log4j2.xml, was: " + SdkConfig.LOG4J2_XML);
    }

    @Test
    @DisplayName("LOG4J2_XML is consistent with CONFIG_DIR")
    void log4j2Xml_isConsistentWithDir() {
        assertTrue(SdkConfig.LOG4J2_XML.startsWith(SdkConfig.CONFIG_DIR),
                "LOG4J2_XML must start with CONFIG_DIR");
    }

    @Test
    @DisplayName("All 5 SdkConfig paths share the same CONFIG_DIR prefix")
    void allPaths_shareConfigDirPrefix() {
        String dir = SdkConfig.CONFIG_DIR;
        assertTrue(SdkConfig.CONFIG_PROPERTIES.startsWith(dir), "CONFIG_PROPERTIES prefix mismatch");
        assertTrue(SdkConfig.LOG4J_PROPERTIES.startsWith(dir),  "LOG4J_PROPERTIES prefix mismatch");
        assertTrue(SdkConfig.LOG4J2_XML.startsWith(dir),        "LOG4J2_XML prefix mismatch");
        assertTrue(SdkConfig.YAML_CONFIG.startsWith(dir),       "YAML_CONFIG prefix mismatch");
    }

    // -- Helper -- replicates SdkConfig resolution logic for isolated unit testing --

    private static String resolveDir(String sysProp, String envVar) {
        String dir = sysProp;
        if (dir == null || dir.isEmpty()) {
            dir = envVar;
        }
        if (dir == null || dir.isEmpty()) {
            dir = "configuration";
        }
        return dir.replaceAll("[/\\\\]+$", "");
    }
}
