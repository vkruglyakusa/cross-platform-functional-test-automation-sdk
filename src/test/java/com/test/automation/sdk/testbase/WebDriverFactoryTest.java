package com.test.automation.sdk.testbase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WebDriverFactory#setupLocalChromeDriver(String)}.
 *
 * Tests cover the chromedriver resolution logic without launching a real browser.
 * The method is package-private specifically to allow this isolated testing.
 *
 * Does NOT require a browser or network connection. Tagged "browser" tests
 * (actual driver launch) are excluded from the standard Maven build.
 */
@DisplayName("WebDriverFactory -- chromedriver resolution logic")
class WebDriverFactoryTest {

    private static final String DRIVER_PROP = "webdriver.chrome.driver";

    @TempDir
    File tempDir;

    private String previousDriverProp;

    @BeforeEach
    void saveSystemProperty() {
        previousDriverProp = System.getProperty(DRIVER_PROP);
        System.clearProperty(DRIVER_PROP);
    }

    @AfterEach
    void restoreSystemProperty() {
        if (previousDriverProp != null) {
            System.setProperty(DRIVER_PROP, previousDriverProp);
        } else {
            System.clearProperty(DRIVER_PROP);
        }
    }

    // -------------------------------------------------------------------------
    // Null / empty / blank path -> false, no system property set
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("null path -> returns false, system property not set")
    void nullPath_returnsFalse() {
        assertFalse(WebDriverFactory.setupLocalChromeDriver(null));
        assertNull(System.getProperty(DRIVER_PROP));
    }

    @Test
    @DisplayName("empty string -> returns false, system property not set")
    void emptyPath_returnsFalse() {
        assertFalse(WebDriverFactory.setupLocalChromeDriver(""));
        assertNull(System.getProperty(DRIVER_PROP));
    }

    @Test
    @DisplayName("blank/whitespace string -> returns false, system property not set")
    void blankPath_returnsFalse() {
        assertFalse(WebDriverFactory.setupLocalChromeDriver("   "));
        assertNull(System.getProperty(DRIVER_PROP));
    }

    // -------------------------------------------------------------------------
    // Non-existent path -> false, no system property set
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("path to non-existent file -> returns false, system property not set")
    void nonExistentFile_returnsFalse() {
        String fakePath = new File(tempDir, "does_not_exist.exe").getAbsolutePath();
        assertFalse(WebDriverFactory.setupLocalChromeDriver(fakePath));
        assertNull(System.getProperty(DRIVER_PROP));
    }

    @Test
    @DisplayName("path to a directory (not a file) -> returns false, system property not set")
    void directoryPath_returnsFalse() {
        assertFalse(WebDriverFactory.setupLocalChromeDriver(tempDir.getAbsolutePath()));
        assertNull(System.getProperty(DRIVER_PROP));
    }

    // -------------------------------------------------------------------------
    // Existing file -> true, system property set to absolute path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("path to existing file -> returns true, system property set")
    void existingFile_returnsTrue_andSetsProperty() throws IOException {
        File fakeDriver = new File(tempDir, "chromedriver.exe");
        assertTrue(fakeDriver.createNewFile());

        assertTrue(WebDriverFactory.setupLocalChromeDriver(fakeDriver.getAbsolutePath()));
        assertEquals(fakeDriver.getAbsolutePath(), System.getProperty(DRIVER_PROP));
    }

    @Test
    @DisplayName("path with surrounding whitespace -> trimmed, file found, returns true")
    void pathWithWhitespace_trimmedAndResolved() throws IOException {
        File fakeDriver = new File(tempDir, "chromedriver");
        assertTrue(fakeDriver.createNewFile());

        String paddedPath = "  " + fakeDriver.getAbsolutePath() + "  ";
        assertTrue(WebDriverFactory.setupLocalChromeDriver(paddedPath));
        assertEquals(fakeDriver.getAbsolutePath(), System.getProperty(DRIVER_PROP));
    }

    @Test
    @DisplayName("existing file on second call overwrites previous system property")
    void secondCall_overwritesPreviousProperty() throws IOException {
        File driver1 = new File(tempDir, "chromedriver_v1.exe");
        File driver2 = new File(tempDir, "chromedriver_v2.exe");
        assertTrue(driver1.createNewFile());
        assertTrue(driver2.createNewFile());

        WebDriverFactory.setupLocalChromeDriver(driver1.getAbsolutePath());
        assertEquals(driver1.getAbsolutePath(), System.getProperty(DRIVER_PROP));

        WebDriverFactory.setupLocalChromeDriver(driver2.getAbsolutePath());
        assertEquals(driver2.getAbsolutePath(), System.getProperty(DRIVER_PROP));
    }

    @Test
    void remoteCapabilities_supportsAllWebBrowsersWithoutLaunchingThem() {
        assertEquals("chrome", WebDriverFactory.remoteCapabilities("chrome").getBrowserName());
        assertEquals("firefox", WebDriverFactory.remoteCapabilities("firefox").getBrowserName());
        assertEquals("MicrosoftEdge", WebDriverFactory.remoteCapabilities("edge").getBrowserName());
    }

    @Test
    void remoteCapabilities_rejectsUnknownBrowser() {
        assertThrows(IllegalArgumentException.class,
                () -> WebDriverFactory.remoteCapabilities("safari"));
    }
}
