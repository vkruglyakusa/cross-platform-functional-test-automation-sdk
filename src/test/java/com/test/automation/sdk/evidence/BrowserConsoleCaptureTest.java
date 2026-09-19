package com.test.automation.sdk.evidence;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.Logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BrowserConsoleCapture} -- SDK v1.5.1 browser console
 * log evidence, fail-safe on unsupported browsers/retrieval errors.
 */
@DisplayName("BrowserConsoleCapture -- fail-safe browser console log evidence")
class BrowserConsoleCaptureTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("writes a readable artifact when the driver exposes browser console entries")
    void writesArtifact_whenLogEntriesAvailable() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        WebDriver.Options options = Mockito.mock(WebDriver.Options.class);
        Logs logs = Mockito.mock(Logs.class);
        LogEntry entry = new LogEntry(java.util.logging.Level.SEVERE, System.currentTimeMillis(), "Uncaught TypeError: x is not a function");
        LogEntries entries = new LogEntries(Collections.singletonList(entry));

        Mockito.when(driver.manage()).thenReturn(options);
        Mockito.when(options.logs()).thenReturn(logs);
        Mockito.when(logs.get(LogType.BROWSER)).thenReturn(entries);

        Path artifact = BrowserConsoleCapture.capture(driver, tempDir, "myTest");

        assertNotNull(artifact, "Artifact should be written when console entries are present");
        assertTrue(Files.exists(artifact));
    }

    @Test
    @DisplayName("returns null (no exception) when the browser does not support console log retrieval")
    void returnsNull_whenUnsupportedBrowser() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        WebDriver.Options options = Mockito.mock(WebDriver.Options.class);
        Logs logs = Mockito.mock(Logs.class);

        Mockito.when(driver.manage()).thenReturn(options);
        Mockito.when(options.logs()).thenReturn(logs);
        Mockito.when(logs.get(LogType.BROWSER)).thenThrow(new org.openqa.selenium.WebDriverException("not supported"));

        Path artifact = BrowserConsoleCapture.capture(driver, tempDir, "myTest");

        assertNull(artifact, "Unsupported browser must fail safe -- no exception, no artifact");
    }

    @Test
    @DisplayName("returns null when the driver has no manage()/logs() support at all (plain mock -> null chain)")
    void returnsNull_whenDriverDoesNotSupportManage() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        Path artifact = BrowserConsoleCapture.capture(driver, tempDir, "myTest");
        assertNull(artifact, "A driver returning null from manage() must fail safe");
    }

    @Test
    @DisplayName("returns null when the console log is empty")
    void returnsNull_whenLogIsEmpty() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        WebDriver.Options options = Mockito.mock(WebDriver.Options.class);
        Logs logs = Mockito.mock(Logs.class);
        LogEntries entries = new LogEntries(Collections.<LogEntry>emptyList());

        Mockito.when(driver.manage()).thenReturn(options);
        Mockito.when(options.logs()).thenReturn(logs);
        Mockito.when(logs.get(LogType.BROWSER)).thenReturn(entries);

        Path artifact = BrowserConsoleCapture.capture(driver, tempDir, "myTest");

        assertNull(artifact, "An empty console log is acceptable and yields no artifact");
    }

    @Test
    @DisplayName("null driver is a safe no-op")
    void nullDriver_isSafeNoOp() {
        assertNull(BrowserConsoleCapture.capture(null, tempDir, "myTest"));
    }
}
