package com.test.automation.sdk.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code config.YamlConfigReader} facade (Phase 3 of the
 * unified web+mobile SDK architecture). Verifies it forwards to
 * {@code utility.YamlConfigReader} without altering values, and that the
 * new Phase 3 mobile default keys ({@code appium.}, {@code android.}, {@code ios.}) are reachable
 * through it.
 */
@DisplayName("config.YamlConfigReader -- platform-neutral facade")
class YamlConfigReaderTest {

    @Test
    @DisplayName("get(key) matches the underlying utility.YamlConfigReader value")
    void get_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.utility.YamlConfigReader.get("browser.default"),
                YamlConfigReader.get("browser.default"));
    }

    @Test
    @DisplayName("get(key, default) matches the underlying utility.YamlConfigReader value")
    void getWithDefault_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.utility.YamlConfigReader.get("nonexistent.key", "fallback"),
                YamlConfigReader.get("nonexistent.key", "fallback"));
    }

    @Test
    @DisplayName("getBoolean() matches the underlying utility.YamlConfigReader value")
    void getBoolean_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.utility.YamlConfigReader.getBoolean("browser.headless", true),
                YamlConfigReader.getBoolean("browser.headless", true));
    }

    @Test
    @DisplayName("getInt() matches the underlying utility.YamlConfigReader value")
    void getInt_matchesUnderlyingReader() {
        assertEquals(
                com.test.automation.sdk.utility.YamlConfigReader.getInt("browser.pageLoadTimeoutSeconds", 0),
                YamlConfigReader.getInt("browser.pageLoadTimeoutSeconds", 0));
    }

    @Test
    @DisplayName("getAll() returns the same map contents as the underlying utility.YamlConfigReader")
    void getAll_matchesUnderlyingReader() {
        Map<String, String> viaFacade = YamlConfigReader.getAll();
        Map<String, String> viaLegacy = com.test.automation.sdk.utility.YamlConfigReader.getAll();
        assertEquals(viaLegacy, viaFacade);
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
