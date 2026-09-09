package com.test.automation.sdk.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the deprecated {@code utility.YamlConfigReader} compatibility
 * facade (Structure Cleanup Phase 2). Verifies it forwards to
 * {@code config.YamlConfigReader}, which now owns the real parser/singleton,
 * without altering values. The full parsing/defaults test suite lives at
 * {@code com.test.automation.sdk.config.YamlConfigReaderTest}.
 */
@DisplayName("utility.YamlConfigReader (deprecated) -- compatibility facade")
class YamlConfigReaderTest {

    @Test
    @DisplayName("get(key) matches the underlying config.YamlConfigReader value")
    void get_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.config.YamlConfigReader.get("browser.default"),
                YamlConfigReader.get("browser.default"));
    }

    @Test
    @DisplayName("get(key, default) matches the underlying config.YamlConfigReader value")
    void getWithDefault_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.config.YamlConfigReader.get("nonexistent.key", "fallback"),
                YamlConfigReader.get("nonexistent.key", "fallback"));
    }

    @Test
    @DisplayName("getBoolean() matches the underlying config.YamlConfigReader value")
    void getBoolean_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.config.YamlConfigReader.getBoolean("browser.headless", true),
                YamlConfigReader.getBoolean("browser.headless", true));
    }

    @Test
    @DisplayName("getInt() matches the underlying config.YamlConfigReader value")
    void getInt_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.config.YamlConfigReader.getInt("browser.pageLoadTimeoutSeconds", 0),
                YamlConfigReader.getInt("browser.pageLoadTimeoutSeconds", 0));
    }

    @Test
    @DisplayName("getAll() returns the same map contents as the underlying config.YamlConfigReader")
    void getAll_matchesUnderlyingReader() {
        Map<String, String> viaFacade = YamlConfigReader.getAll();
        Map<String, String> viaReal = com.test.automation.sdk.config.YamlConfigReader.getAll();
        assertEquals(viaReal, viaFacade);
    }

    @Test
    @DisplayName("appium.localUrl default is reachable through the facade")
    void appiumLocalUrlDefault_isReachable() {
        assertEquals("http://127.0.0.1:4723/", YamlConfigReader.get("appium.localUrl"));
    }

    @Test
    @DisplayName("android.automationName default is reachable through the facade")
    void androidAutomationNameDefault_isReachable() {
        assertEquals("UiAutomator2", YamlConfigReader.get("android.automationName"));
    }

    @Test
    @DisplayName("ios.automationName default is reachable through the facade")
    void iosAutomationNameDefault_isReachable() {
        assertEquals("XCUITest", YamlConfigReader.get("ios.automationName"));
    }
}
