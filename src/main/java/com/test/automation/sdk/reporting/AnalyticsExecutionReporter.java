package com.test.automation.sdk.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.test.automation.sdk.config.ConfigurationManager;

/**
 * Appends every {@link ExecutionEvent} as one JSON line to a per-run file under
 * {@code reporting.analytics.directory}, building a historical event store that
 * powers cross-run analysis -- pass-rate trends, flaky-test detection, and
 * healed-locator frequency (see {@link AnalyticsTrendReport}) -- none of which a
 * single Allure/Extent report (scoped to one run) can answer on its own.
 *
 * <p>One file per JVM run (named at first write), so successive {@code mvn test}
 * executions accumulate a directory of run files rather than overwriting one
 * another. Never throws: a write failure disables this reporter for the rest of
 * the JVM lifetime rather than affecting test execution, matching the
 * report-generation-is-a-separate-concern philosophy used by
 * {@link AllureReportGenerator}.
 */
public final class AnalyticsExecutionReporter implements ExecutionReporter {

    private static final Logger log = LogManager.getLogger(AnalyticsExecutionReporter.class);
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Object writeLock = new Object();
    private volatile Path runFile;
    private volatile boolean disabled;

    @Override
    public void report(ExecutionEvent event) {
        if (event == null || disabled || !ConfigurationManager.getAnalyticsConfig().enabled()) {
            return;
        }
        try {
            Path file = ensureRunFile();
            String line = toJsonLine(event).toString();
            synchronized (writeLock) {
                Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            disabled = true;
            log.warn("AnalyticsExecutionReporter: disabling after write failure: " + e.getMessage());
        }
    }

    private Path ensureRunFile() throws IOException {
        Path file = runFile;
        if (file != null) {
            return file;
        }
        synchronized (writeLock) {
            if (runFile == null) {
                Path dir = Paths.get(ConfigurationManager.getAnalyticsConfig().directory());
                Files.createDirectories(dir);
                runFile = dir.resolve("execution-events-" + FILE_STAMP.format(Instant.now()) + ".jsonl");
            }
            return runFile;
        }
    }

    static JSONObject toJsonLine(ExecutionEvent event) {
        JSONObject json = new JSONObject();
        json.put("type", event.getType() != null ? event.getType().name() : "");
        json.put("status", event.getStatus() != null ? event.getStatus().name() : "");
        json.put("timestampMillis", event.getTimestampMillis());
        putIfNotEmpty(json, "suiteName", event.getSuiteName());
        putIfNotEmpty(json, "testNgTestName", event.getTestNgTestName());
        putIfNotEmpty(json, "className", event.getClassName());
        putIfNotEmpty(json, "methodName", event.getMethodName());
        putIfNotEmpty(json, "testCaseName", event.getTestCaseName());
        putIfNotEmpty(json, "action", event.getAction());
        putIfNotEmpty(json, "locator", event.getLocator());
        putIfNotEmpty(json, "message", event.getMessage());
        if (event.getDurationMillis() != null) {
            json.put("durationMillis", event.getDurationMillis());
        }
        if (event.getThrowable() != null) {
            putIfNotEmpty(json, "throwable", String.valueOf(event.getThrowable()));
        }
        return json;
    }

    private static void putIfNotEmpty(JSONObject json, String key, String value) {
        if (value != null && !value.isEmpty()) {
            json.put(key, value);
        }
    }

    /** Test-only accessor -- lets unit tests point this instance at a known path deterministically. */
    Path currentRunFile() {
        return runFile;
    }
}
