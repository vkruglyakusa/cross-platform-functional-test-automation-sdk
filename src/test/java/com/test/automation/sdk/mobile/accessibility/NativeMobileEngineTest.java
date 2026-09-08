package com.test.automation.sdk.mobile.accessibility;

import com.test.automation.sdk.accessibility.AccessibilityEngine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Unit tests for {@link NativeMobileEngine} — metadata and the non-Appium driver guard. */
@DisplayName("NativeMobileEngine - AccessibilityEngine metadata and driver-type guard")
class NativeMobileEngineTest {

    @Test
    @DisplayName("reports its engine name and version")
    void reportsEngineNameAndVersion() {
        AccessibilityEngine engine = new NativeMobileEngine();
        assertEquals("NativeMobile", engine.getEngineName());
        assertNotNull(engine.getEngineVersion());
    }

    @Test
    @DisplayName("scan() rejects a non-AppiumDriver WebDriver with a clear error instead of a ClassCastException")
    void scanRejectsNonAppiumDriver() {
        AccessibilityEngine engine = new NativeMobileEngine();
        WebDriver notAppium = mock(ChromeDriver.class);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> engine.scan(notAppium, "SomeScreen"));
        assertTrue(ex.getMessage().contains("AppiumDriver"));
    }
}
