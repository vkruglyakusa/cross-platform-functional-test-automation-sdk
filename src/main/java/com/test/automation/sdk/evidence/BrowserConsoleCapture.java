package com.test.automation.sdk.evidence;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

/**
 * SDK v1.5.1 -- browser console log evidence for a failed Web test.
 *
 * <h3>Actual browser support</h3>
 * <ul>
 *   <li><b>Chrome / Edge (Chromium-based)</b> -- supported via Selenium's
 *       standard {@code driver.manage().logs().get(LogType.BROWSER)} API,
 *       provided the session was created with the {@code goog:loggingPrefs}
 *       (Chrome) / {@code ms:loggingPrefs} (Edge) capability enabled -- see
 *       {@code WebDriverFactory}, controlled by
 *       {@code logging.enableBrowserConsoleLogs} (default {@code true}).</li>
 *   <li><b>Firefox</b> -- NOT reliably supported. geckodriver dropped support
 *       for the legacy JSON Wire {@code LogType.BROWSER} endpoint; calling
 *       {@code logs().get(LogType.BROWSER)} on Firefox typically throws or
 *       returns an empty/unsupported result. This method treats that as a
 *       normal, fail-safe empty result -- it never fails the test.</li>
 * </ul>
 *
 * <p>Capture is a point-in-time drain of the browser's own log buffer
 * (entries accumulated since session start or the last drain) -- no
 * continuous in-process listener is required, unlike
 * {@link NetworkTraceRecorder}.</p>
 */
public final class BrowserConsoleCapture {

    private static final Logger log = LogManager.getLogger(BrowserConsoleCapture.class);
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private BrowserConsoleCapture() {
    }

    /**
     * Retrieves the browser's console log (if supported) and writes it to a
     * {@code *_console.log} file under {@code outputDir}. Fail-safe: returns
     * {@code null} (never throws) when the driver session is gone, the
     * browser does not support console log retrieval, or the log is empty.
     *
     * @param driver     active WebDriver session
     * @param outputDir  directory the artifact is written to (created if needed)
     * @param namePrefix file name prefix, typically the failing test's name
     * @return path to the written artifact, or {@code null} if nothing was captured
     */
    public static Path capture(WebDriver driver, File outputDir, String namePrefix) {
        if (driver == null) {
            return null;
        }
        List<LogEntry> logEntries;
        try {
            LogEntries entries = driver.manage().logs().get(LogType.BROWSER);
            logEntries = entries == null ? null : entries.getAll();
        } catch (Exception e) {
            log.info("[BrowserConsoleCapture] Browser console log not available for this driver/browser "
                    + "(unsupported or session closed) -- skipped: {}", e.getMessage());
            return null;
        }
        if (logEntries == null || logEntries.isEmpty()) {
            log.debug("[BrowserConsoleCapture] No browser console entries captured");
            return null;
        }

        try {
            Files.createDirectories(outputDir.toPath());
            StringBuilder content = new StringBuilder();
            content.append("# Browser console log -- captured at test failure\n");
            for (LogEntry entry : logEntries) {
                content.append('[').append(Instant.ofEpochMilli(entry.getTimestamp())).append("] ")
                        .append(entry.getLevel()).append(' ')
                        .append(entry.getMessage()).append('\n');
            }
            String safeName = namePrefix.replaceAll("[^a-zA-Z0-9_-]", "_");
            Path file = outputDir.toPath().resolve(safeName + "_" + FILE_STAMP.format(Instant.now()) + "_console.log");
            Files.write(file, content.toString().getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (Exception e) {
            log.warn("[BrowserConsoleCapture] Failed to write browser console log artifact: {}", e.getMessage());
            return null;
        }
    }
}
