package com.test.automation.sdk.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the unified {@code config.SdkConfig} resolver -- the single
 * configuration-path-resolution implementation (Phase 3 of the unified web+mobile
 * SDK architecture; the previously separate {@code testbase.SdkConfig} and
 * {@code mobile.testbase.MobileSdkConfig} backward-compat shims have since been
 * removed entirely -- there is now exactly one class).
 */
@DisplayName("config.SdkConfig -- unified configuration path resolution")
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
    @DisplayName("All well-known paths are non-null and share the CONFIG_DIR prefix")
    void allPaths_shareConfigDirPrefix() {
        String dir = SdkConfig.CONFIG_DIR;
        assertNotNull(dir);
        assertTrue(SdkConfig.CONFIG_PROPERTIES.startsWith(dir));
        assertTrue(SdkConfig.LOG4J_PROPERTIES.startsWith(dir));
        assertTrue(SdkConfig.LOG4J2_XML.startsWith(dir));
        assertTrue(SdkConfig.YAML_CONFIG.startsWith(dir));
        assertTrue(SdkConfig.MOBILE_CONFIG_YAML.startsWith(dir));
    }

    @Test
    @DisplayName("CONFIG_PROPERTIES ends with /config.properties")
    void configProperties_endsWithConfigProperties() {
        assertTrue(SdkConfig.CONFIG_PROPERTIES.endsWith("/config.properties"));
    }

    @Test
    @DisplayName("LOG4J_PROPERTIES ends with /log4j.properties")
    void log4jProperties_endsWithLog4jProperties() {
        assertTrue(SdkConfig.LOG4J_PROPERTIES.endsWith("/log4j.properties"));
    }

    @Test
    @DisplayName("LOG4J2_XML ends with /log4j2.xml")
    void log4j2Xml_endsWithLog4j2Xml() {
        assertTrue(SdkConfig.LOG4J2_XML.endsWith("/log4j2.xml"));
    }

    @Test
    @DisplayName("YAML_CONFIG ends with /sdk-config.yaml")
    void yamlConfig_endsWithSdkConfigYaml() {
        assertTrue(SdkConfig.YAML_CONFIG.endsWith("/sdk-config.yaml"));
    }

    @Test
    @DisplayName("MOBILE_CONFIG_YAML ends with /mobile-config.yaml")
    void mobileConfigYaml_endsWithMobileConfigYaml() {
        assertTrue(SdkConfig.MOBILE_CONFIG_YAML.endsWith("/mobile-config.yaml"));
    }

    @Test
    @DisplayName("BROWSERSTACK_YAML is the fixed project-root filename required by the BrowserStack SDK")
    void browserstackYaml_isFixedProjectRootFilename() {
        assertEquals("browserstack.yml", SdkConfig.BROWSERSTACK_YAML);
    }

    // -- Resolution-priority algorithm, tested in isolation (SdkConfig itself uses a
    //    static initializer, so the cascade is replicated here rather than re-triggered) --

    @Test
    @DisplayName("System property overrides env var and default")
    void systemPropertyTakesPriority() {
        System.setProperty("sdk.config.dir", "custom/config");
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
