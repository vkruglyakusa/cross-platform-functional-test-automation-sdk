package com.test.automation.sdk.mobile.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link MobileConfigReader}'s Phase 3 fallback behavior
 * (unified web+mobile SDK architecture): when no standalone
 * mobile-config.yaml exists (the case for this SDK's own repo -- only a
 * mobile-config.yaml.example template is checked in), reads must fall back
 * to the unified sdk-config.yaml's built-in {@code android.} / {@code ios.} /
 * {@code appium.} defaults via {@code config.YamlConfigReader}, instead of
 * being limited to MobileConfigReader's own old hardcoded 3-key default set.
 *
 * <p>Also verifies Unified SDK Review Priority 2 (section 8 of
 * docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md):
 * {@link MobileConfigReader} now delegates to
 * {@link com.test.automation.sdk.config.ConfigurationManager} for any key
 * not present in a standalone mobile-config.yaml, so a system property (the
 * highest-precedence tier of the one shared resolution chain) overrides the
 * built-in default the same way it would for Web/common configuration.</p>
 */
@DisplayName("MobileConfigReader -- Phase 3 unified sdk-config.yaml fallback")
class MobileConfigReaderTest {

    @Test
    @DisplayName("appium.localUrl falls back to the unified sdk-config.yaml default")
    void appiumLocalUrl_fallsBackToUnifiedDefault() {
        assertEquals("http://127.0.0.1:4723/", MobileConfigReader.get("appium.localUrl", "should-not-be-used"));
    }

    @Test
    @DisplayName("android.automationName falls back to the unified sdk-config.yaml default")
    void androidAutomationName_fallsBackToUnifiedDefault() {
        assertEquals("UiAutomator2", MobileConfigReader.get("android.automationName", "should-not-be-used"));
    }

    @Test
    @DisplayName("ios.automationName falls back to the unified sdk-config.yaml default")
    void iosAutomationName_fallsBackToUnifiedDefault() {
        assertEquals("XCUITest", MobileConfigReader.get("ios.automationName", "should-not-be-used"));
    }

    @Test
    @DisplayName("A key present in neither mobile-config.yaml nor sdk-config.yaml returns the caller's default")
    void unknownKey_returnsCallerDefault() {
        assertEquals("fallbackValue", MobileConfigReader.get("android.totallyUnknownKey", "fallbackValue"));
    }

    @Test
    @DisplayName("A system property overrides the unified sdk-config.yaml default (shared precedence chain)")
    void systemProperty_overridesUnifiedYamlDefault() {
        System.setProperty("android.automationName", "Espresso");
        try {
            assertEquals("Espresso", MobileConfigReader.get("android.automationName", "should-not-be-used"));
        } finally {
            System.clearProperty("android.automationName");
        }
    }

    @Test
    @DisplayName("getMobileConfig() exposes a typed view resolved through the same MobileConfigReader.get() chain")
    void getMobileConfig_exposesTypedView() {
        MobileConfigReader.MobileConfig mobileConfig = MobileConfigReader.getMobileConfig();

        assertEquals("http://127.0.0.1:4723/", mobileConfig.appiumLocalUrl());
        assertEquals("UiAutomator2", mobileConfig.androidAutomationName());
        assertEquals("XCUITest", mobileConfig.iosAutomationName());
    }

    /*
     * Final-cleanup fix (prior to the v1.1.0 release): a system property or
     * environment variable must always win over a value already committed to
     * a standalone mobile-config.yaml, using the same ConfigurationManager
     * precedence chain (no second Mobile-specific resolver).
     */

    @Test
    @DisplayName("A system property overrides a value present in a standalone mobile-config.yaml")
    void systemProperty_overridesStandaloneMobileConfigYamlValue() throws Exception {
        java.io.File yamlFile = new java.io.File(com.test.automation.sdk.config.SdkConfig.MOBILE_CONFIG_YAML);
        java.io.File parent = yamlFile.getParentFile();
        boolean createdParent = parent != null && !parent.exists() && parent.mkdirs();
        boolean fileExistedBefore = yamlFile.exists();
        try {
            java.nio.file.Files.write(yamlFile.toPath(),
                    "android:\n  automationName: FileValue\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            MobileConfigReader.resetForTests();

            // No override set -- the standalone file's value must win over the project-YAML default.
            assertEquals("FileValue", MobileConfigReader.get("android.automationName", "should-not-be-used"));

            // A system property override must win over the standalone file's value.
            System.setProperty("android.automationName", "Espresso");
            try {
                assertEquals("Espresso", MobileConfigReader.get("android.automationName", "should-not-be-used"));
            } finally {
                System.clearProperty("android.automationName");
            }
        } finally {
            if (!fileExistedBefore) {
                yamlFile.delete();
            }
            if (createdParent) {
                parent.delete();
            }
            MobileConfigReader.resetForTests();
        }
    }
}

