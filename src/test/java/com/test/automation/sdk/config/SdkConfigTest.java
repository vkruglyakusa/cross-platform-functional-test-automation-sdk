package com.test.automation.sdk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the unified {@code config.SdkConfig} resolver and its
 * delegation into the legacy {@code testbase.SdkConfig} /
 * {@code mobile.testbase.MobileSdkConfig} classes (Phase 3 of the unified
 * web+mobile SDK architecture).
 */
@DisplayName("config.SdkConfig -- unified configuration path resolution")
class SdkConfigTest {

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

    @Test
    @DisplayName("Legacy testbase.SdkConfig delegates its fields to config.SdkConfig")
    void legacyWebSdkConfig_delegatesToUnifiedConfig() {
        assertEquals(SdkConfig.CONFIG_DIR, com.test.automation.sdk.testbase.SdkConfig.CONFIG_DIR);
        assertEquals(SdkConfig.CONFIG_PROPERTIES, com.test.automation.sdk.testbase.SdkConfig.CONFIG_PROPERTIES);
        assertEquals(SdkConfig.LOG4J_PROPERTIES, com.test.automation.sdk.testbase.SdkConfig.LOG4J_PROPERTIES);
        assertEquals(SdkConfig.LOG4J2_XML, com.test.automation.sdk.testbase.SdkConfig.LOG4J2_XML);
        assertEquals(SdkConfig.YAML_CONFIG, com.test.automation.sdk.testbase.SdkConfig.YAML_CONFIG);
    }

    @Test
    @DisplayName("Legacy mobile.testbase.MobileSdkConfig delegates its fields to config.SdkConfig")
    void legacyMobileSdkConfig_delegatesToUnifiedConfig() {
        assertEquals(SdkConfig.CONFIG_DIR, com.test.automation.sdk.mobile.testbase.MobileSdkConfig.CONFIG_DIR);
        assertEquals(SdkConfig.MOBILE_CONFIG_YAML,
                com.test.automation.sdk.mobile.testbase.MobileSdkConfig.MOBILE_CONFIG_YAML);
        assertEquals(SdkConfig.BROWSERSTACK_YAML,
                com.test.automation.sdk.mobile.testbase.MobileSdkConfig.BROWSERSTACK_YAML);
        assertEquals(SdkConfig.LOG4J2_XML, com.test.automation.sdk.mobile.testbase.MobileSdkConfig.LOG4J2_XML);
    }
}
