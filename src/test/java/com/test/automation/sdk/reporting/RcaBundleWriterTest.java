package com.test.automation.sdk.reporting;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RcaBundleWriter} -- the Tier 3 (#9) automated
 * RCA-to-fix bundle: one consolidated JSON file per test failure combining
 * test identity, exception chain, evidence paths, and a fresh SDK log tail.
 */
@DisplayName("RcaBundleWriter -- consolidated per-failure RCA JSON bundle")
class RcaBundleWriterTest {

    @TempDir
    File tempDir;

    @AfterEach
    void clearOverrides() {
        System.clearProperty("reporting.rcaBundle.enabled");
        System.clearProperty("reporting.rcaBundle.directory");
        System.clearProperty("reporting.rcaBundle.logTailLines");
        System.clearProperty("reporting.rcaBundle.stackTraceFrames");
        System.clearProperty("reporting.logsDir");
    }

    @Test
    @DisplayName("writes a JSON file containing test identity, exception, and evidence")
    void writesBundleWithCoreFields() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());

        Path screenshot = new File(tempDir, "shot.png").toPath();
        Path dom = new File(tempDir, "dom.html").toPath();
        Files.write(screenshot, "png-bytes".getBytes(StandardCharsets.UTF_8));
        Files.write(dom, "<html></html>".getBytes(StandardCharsets.UTF_8));

        Exception exception = new IllegalStateException("Element not found");

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .testCaseName("ADO-42")
                .className("LoginTest")
                .methodName("testValidLogin")
                .suiteName("Regression Suite")
                .testNgTestName("Web Tests")
                .platform("web")
                .browser("chrome")
                .lastCompletedStep("Clicking Submit button")
                .throwable(exception)
                .evidence(Arrays.asList(
                        ExecutionEvidence.screenshot("ADO-42", screenshot),
                        ExecutionEvidence.domDump("ADO-42", dom))));

        assertNotNull(bundleFile, "Bundle file should be written");
        assertTrue(Files.exists(bundleFile));

        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        assertEquals(1, json.getInt("schemaVersion"));
        assertEquals("FAILED", json.getString("status"));

        JSONObject test = json.getJSONObject("test");
        assertEquals("ADO-42", test.getString("testCaseName"));
        assertEquals("LoginTest", test.getString("className"));
        assertEquals("testValidLogin", test.getString("methodName"));
        assertEquals("Regression Suite", test.getString("suiteName"));
        assertEquals("Clicking Submit button", test.getString("lastCompletedStep"));

        JSONObject exceptionJson = json.getJSONObject("exception");
        JSONArray chain = exceptionJson.getJSONArray("chain");
        assertEquals(1, chain.length());
        assertEquals("java.lang.IllegalStateException", chain.getJSONObject(0).getString("type"));
        assertEquals("Element not found", chain.getJSONObject(0).getString("message"));
        assertTrue(chain.getJSONObject(0).getJSONArray("stackTrace").length() > 0);

        JSONArray evidence = json.getJSONArray("evidence");
        assertEquals(2, evidence.length());
        assertEquals("screenshot", evidence.getJSONObject(0).getString("type"));
        assertEquals("dom", evidence.getJSONObject(1).getString("type"));

        assertTrue(json.getJSONArray("suggestedNextSteps").length() > 0);
    }

    @Test
    @DisplayName("captures a nested exception cause chain")
    void capturesNestedCauseChain() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());

        Exception cause = new NullPointerException("root cause");
        Exception wrapper = new RuntimeException("wrapper failure", cause);

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .methodName("testSomething")
                .throwable(wrapper));

        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        JSONArray chain = json.getJSONObject("exception").getJSONArray("chain");
        assertEquals(2, chain.length());
        assertEquals("java.lang.RuntimeException", chain.getJSONObject(0).getString("type"));
        assertEquals("java.lang.NullPointerException", chain.getJSONObject(1).getString("type"));
    }

    @Test
    @DisplayName("tails the shared SDK log file, respecting the configured line limit")
    void tailsSdkLogFile() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());
        System.setProperty("reporting.rcaBundle.logTailLines", "2");
        File logsDir = new File(tempDir, "logs");
        logsDir.mkdirs();
        System.setProperty("reporting.logsDir", logsDir.getAbsolutePath());
        Files.write(new File(logsDir, "sdk.log").toPath(),
                Arrays.asList("line1", "line2", "line3", "line4"), StandardCharsets.UTF_8);

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .methodName("testSomething")
                .throwable(new RuntimeException("boom")));

        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        List<Object> lines = json.getJSONArray("recentLogLines").toList();
        assertEquals(2, lines.size());
        assertEquals("line3", lines.get(0));
        assertEquals("line4", lines.get(1));
    }

    @Test
    @DisplayName("missing log file -- recentLogLines is an empty array, no exception thrown")
    void missingLogFileYieldsEmptyArray() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());
        System.setProperty("reporting.logsDir", new File(tempDir, "does-not-exist").getAbsolutePath());

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .methodName("testSomething")
                .throwable(new RuntimeException("boom")));

        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        assertEquals(0, json.getJSONArray("recentLogLines").length());
    }

    @Test
    @DisplayName("disabled via config -- no file is written, returns null")
    void disabledConfigWritesNothing() {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());
        System.setProperty("reporting.rcaBundle.enabled", "false");

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .methodName("testSomething")
                .throwable(new RuntimeException("boom")));

        assertNull(bundleFile);
        assertEquals(0, tempDir.listFiles().length);
    }

    @Test
    @DisplayName("null context is a safe no-op")
    void nullContextIsSafeNoOp() {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());
        assertNull(RcaBundleWriter.write(null));
    }

    @Test
    @DisplayName("evidence entries with a null path are skipped")
    void evidenceWithNullPathIsSkipped() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .methodName("testSomething")
                .throwable(new RuntimeException("boom"))
                .evidence(Arrays.asList(ExecutionEvidence.screenshot("name", null))));

        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        assertEquals(0, json.getJSONArray("evidence").length());
    }

    @Test
    @DisplayName("no throwable -- exception object is empty, bundle still writes")
    void noThrowableStillWrites() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder().methodName("testSomething"));

        assertNotNull(bundleFile);
        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        assertTrue(json.getJSONObject("exception").isEmpty());
    }

    @Test
    @DisplayName("SDK v1.5.1 -- browserConsoleLog and networkTrace are additive optional fields, "
            + "absent when no such evidence is provided (backward compatible with pre-v1.5.1 bundles)")
    void additiveFieldsAbsent_whenNoNewEvidenceTypes() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());

        Path screenshot = new File(tempDir, "shot.png").toPath();
        Files.write(screenshot, "png-bytes".getBytes(StandardCharsets.UTF_8));

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .methodName("testSomething")
                .throwable(new RuntimeException("boom"))
                .evidence(Arrays.asList(ExecutionEvidence.screenshot("shot", screenshot))));

        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        assertFalse(json.has("browserConsoleLog"), "Field must be absent (not null/empty) when no console evidence exists");
        assertFalse(json.has("networkTrace"), "Field must be absent (not null/empty) when no network evidence exists");
    }

    @Test
    @DisplayName("SDK v1.5.1 -- browserConsoleLog and networkTrace are populated when that evidence is present")
    void additiveFieldsPopulated_whenNewEvidenceTypesPresent() throws Exception {
        System.setProperty("reporting.rcaBundle.directory", tempDir.getAbsolutePath());

        Path console = new File(tempDir, "console.log").toPath();
        Path network = new File(tempDir, "trace-network.json").toPath();
        Files.write(console, "console output".getBytes(StandardCharsets.UTF_8));
        Files.write(network, "{}".getBytes(StandardCharsets.UTF_8));

        Path bundleFile = RcaBundleWriter.write(RcaBundleWriter.builder()
                .methodName("testSomething")
                .throwable(new RuntimeException("boom"))
                .evidence(Arrays.asList(
                        ExecutionEvidence.browserConsole("console", console),
                        ExecutionEvidence.networkTrace("trace", network))));

        JSONObject json = new JSONObject(new String(Files.readAllBytes(bundleFile), StandardCharsets.UTF_8));
        assertEquals(console.toString(), json.getString("browserConsoleLog"));
        assertEquals(network.toString(), json.getString("networkTrace"));

        // Both remain present in the generic evidence array too (existing, unchanged behavior).
        JSONArray evidence = json.getJSONArray("evidence");
        assertEquals(2, evidence.length());
    }
}
