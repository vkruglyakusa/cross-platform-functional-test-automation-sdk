package com.test.automation.sdk.mobile.config;

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
}
