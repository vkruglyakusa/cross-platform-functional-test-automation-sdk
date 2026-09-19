package com.test.automation.sdk.reporting;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Reads the historical JSON-lines files written by {@link AnalyticsExecutionReporter}
 * across many separate runs and aggregates them into cross-run insights that a
 * single-run report (Allure/Extent) cannot provide on its own:
 *
 * <ul>
 *   <li>{@link #summarizeTestOutcomes(Path)} -- pass/fail/skip counts per test,
 *       including flaky-test detection (a test that has both passed and failed
 *       across the scanned runs).</li>
 *   <li>{@link #summarizeHealing(Path)} -- how often each locator needed runtime
 *       self-healing ({@code com.test.automation.sdk.healing}), and how often
 *       healing was exhausted (i.e. the locator is genuinely broken, not just
 *       flaky).</li>
 * </ul>
 *
 * Malformed or unreadable lines/files are skipped rather than aborting the whole
 * aggregation -- a single corrupted run file must never hide the rest of the
 * history.
 */
public final class AnalyticsTrendReport {

    private static final String LOCATOR_HEAL_ACTION = "LOCATOR_HEALED";

    private AnalyticsTrendReport() {}

    /** Aggregated pass/fail/skip counts for one test across all scanned runs. */
    public static final class TestOutcome {
        private final String testKey;
        private int passCount;
        private int failCount;
        private int skipCount;

        private TestOutcome(String testKey) {
            this.testKey = testKey;
        }

        public String getTestKey() {
            return testKey;
        }

        public int getPassCount() {
            return passCount;
        }

        public int getFailCount() {
            return failCount;
        }

        public int getSkipCount() {
            return skipCount;
        }

        public int getTotalRuns() {
            return passCount + failCount + skipCount;
        }

        /** A test is flaky when the same test key has both passed and failed across the scanned history. */
        public boolean isFlaky() {
            return passCount > 0 && failCount > 0;
        }
    }

    /** Aggregated self-healing outcomes for one locator across all scanned runs. */
    public static final class HealStats {
        private final String locator;
        private int healedCount;
        private int exhaustedCount;

        private HealStats(String locator) {
            this.locator = locator;
        }

        public String getLocator() {
            return locator;
        }

        public int getHealedCount() {
            return healedCount;
        }

        public int getExhaustedCount() {
            return exhaustedCount;
        }

        public int getTotalAttempts() {
            return healedCount + exhaustedCount;
        }
    }

    /**
     * @param analyticsDirectory the {@code reporting.analytics.directory} folder
     *                           containing one or more {@code *.jsonl} run files
     * @return test outcomes keyed by {@code className.methodName[testCaseName]},
     *         ordered by first appearance
     */
    public static Map<String, TestOutcome> summarizeTestOutcomes(Path analyticsDirectory) throws IOException {
        Map<String, TestOutcome> results = new LinkedHashMap<>();
        for (Path file : listRunFiles(analyticsDirectory)) {
            for (JSONObject event : readEvents(file)) {
                String type = event.optString("type", "");
                if (!"TEST_PASSED".equals(type) && !"TEST_FAILED".equals(type) && !"TEST_SKIPPED".equals(type)) {
                    continue;
                }
                String key = testKey(event);
                if (key.isEmpty()) {
                    continue;
                }
                TestOutcome outcome = results.computeIfAbsent(key, TestOutcome::new);
                switch (type) {
                    case "TEST_PASSED":
                        outcome.passCount++;
                        break;
                    case "TEST_FAILED":
                        outcome.failCount++;
                        break;
                    default:
                        outcome.skipCount++;
                }
            }
        }
        return results;
    }

    /**
     * @param analyticsDirectory the {@code reporting.analytics.directory} folder
     *                           containing one or more {@code *.jsonl} run files
     * @return self-healing outcomes keyed by the original (pre-healing) locator string
     */
    public static Map<String, HealStats> summarizeHealing(Path analyticsDirectory) throws IOException {
        Map<String, HealStats> results = new LinkedHashMap<>();
        for (Path file : listRunFiles(analyticsDirectory)) {
            for (JSONObject event : readEvents(file)) {
                if (!LOCATOR_HEAL_ACTION.equals(event.optString("action", ""))) {
                    continue;
                }
                String locator = event.optString("locator", "");
                if (locator.isEmpty()) {
                    continue;
                }
                HealStats stats = results.computeIfAbsent(locator, HealStats::new);
                String type = event.optString("type", "");
                if ("ACTION_COMPLETED".equals(type)) {
                    stats.healedCount++;
                } else if ("ACTION_FAILED".equals(type)) {
                    stats.exhaustedCount++;
                }
            }
        }
        return results;
    }

    private static String testKey(JSONObject event) {
        String className = event.optString("className", "");
        String methodName = event.optString("methodName", "");
        String testCaseName = event.optString("testCaseName", "");
        if (className.isEmpty() && methodName.isEmpty()) {
            return "";
        }
        String base = className + "." + methodName;
        return testCaseName.isEmpty() ? base : base + "[" + testCaseName + "]";
    }

    private static List<Path> listRunFiles(Path analyticsDirectory) throws IOException {
        List<Path> files = new ArrayList<>();
        if (analyticsDirectory == null || !Files.isDirectory(analyticsDirectory)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(analyticsDirectory, "*.jsonl")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        return files;
    }

    private static List<JSONObject> readEvents(Path file) {
        List<JSONObject> events = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file)) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                try {
                    events.add(new JSONObject(new JSONTokener(line)));
                } catch (Exception malformedLine) {
                    // Skip one bad line, keep reading the rest of the file.
                }
            }
        } catch (IOException unreadableFile) {
            // Skip one unreadable file, keep aggregating the rest of the history.
        }
        return events;
    }
}
