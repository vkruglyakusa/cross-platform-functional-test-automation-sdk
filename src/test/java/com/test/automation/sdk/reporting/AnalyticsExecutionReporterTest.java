package com.test.automation.sdk.reporting;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AnalyticsExecutionReporter}. Verifies the JSON-lines
 * write path in isolation (no real TestNG run needed).
 */
@DisplayName("AnalyticsExecutionReporter -- per-run JSON-lines event persistence")
class AnalyticsExecutionReporterTest {

    @TempDir
    File tempDir;

    @AfterEach
    void clearOverrides() {
        System.clearProperty("reporting.analytics.enabled");
        System.clearProperty("reporting.analytics.directory");
    }

    @Test
    @DisplayName("writes one JSON line per reported event, with core fields populated")
    void writesOneJsonLinePerEvent() throws Exception {
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        AnalyticsExecutionReporter reporter = new AnalyticsExecutionReporter();

        ExecutionEvent event = ExecutionEvent.builder(ExecutionEventType.TEST_PASSED)
                .status(ExecutionStatus.PASSED)
                .className("LoginTest")
                .methodName("testValidLogin")
                .testCaseName("ADO-42")
                .durationMillis(1234L)
                .message("Test passed")
                .build();

        reporter.report(event);

        Path runFile = reporter.currentRunFile();
        assertNotNull(runFile, "Run file should be created on first write");
        List<String> lines = Files.readAllLines(runFile);
        assertEquals(1, lines.size());

        JSONObject json = new JSONObject(lines.get(0));
        assertEquals("TEST_PASSED", json.getString("type"));
        assertEquals("PASSED", json.getString("status"));
        assertEquals("LoginTest", json.getString("className"));
        assertEquals("testValidLogin", json.getString("methodName"));
        assertEquals("ADO-42", json.getString("testCaseName"));
        assertEquals(1234, json.getLong("durationMillis"));
    }

    @Test
    @DisplayName("multiple events append to the same run file, in order")
    void multipleEventsAppendToSameFile() throws Exception {
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        AnalyticsExecutionReporter reporter = new AnalyticsExecutionReporter();

        reporter.report(ExecutionEvent.builder(ExecutionEventType.TEST_STARTED).methodName("m1").build());
        reporter.report(ExecutionEvent.builder(ExecutionEventType.TEST_PASSED).methodName("m1").build());

        Path runFile = reporter.currentRunFile();
        List<String> lines = Files.readAllLines(runFile);
        assertEquals(2, lines.size());
        assertEquals("TEST_STARTED", new JSONObject(lines.get(0)).getString("type"));
        assertEquals("TEST_PASSED", new JSONObject(lines.get(1)).getString("type"));
    }

    @Test
    @DisplayName("disabled via config -- no file is ever created")
    void disabledConfigWritesNothing() {
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        System.setProperty("reporting.analytics.enabled", "false");
        AnalyticsExecutionReporter reporter = new AnalyticsExecutionReporter();

        reporter.report(ExecutionEvent.builder(ExecutionEventType.TEST_PASSED).build());

        assertNull(reporter.currentRunFile());
        assertEquals(0, tempDir.listFiles().length);
    }

    @Test
    @DisplayName("null event is a safe no-op")
    void nullEventIsSafeNoOp() {
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        AnalyticsExecutionReporter reporter = new AnalyticsExecutionReporter();

        reporter.report(null);

        assertNull(reporter.currentRunFile());
    }

    @Test
    @DisplayName("a locator-healed action event carries action+locator fields for trend aggregation")
    void locatorHealedEventCarriesActionAndLocator() throws Exception {
        System.setProperty("reporting.analytics.directory", tempDir.getAbsolutePath());
        AnalyticsExecutionReporter reporter = new AnalyticsExecutionReporter();

        reporter.report(ExecutionEvent.builder(ExecutionEventType.ACTION_COMPLETED)
                .action("LOCATOR_HEALED")
                .locator("By.xpath: //input[@id='email' and @type='text']")
                .message("LoginPage.emailField healed -> //input[@id='email']")
                .build());

        List<String> lines = Files.readAllLines(reporter.currentRunFile());
        JSONObject json = new JSONObject(lines.get(0));
        assertEquals("LOCATOR_HEALED", json.getString("action"));
        assertTrue(json.getString("locator").contains("@id='email'"));
    }
}
