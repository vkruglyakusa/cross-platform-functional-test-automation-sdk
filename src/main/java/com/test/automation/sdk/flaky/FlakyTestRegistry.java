package com.test.automation.sdk.flaky;

import com.test.automation.sdk.reporting.AnalyticsTrendReport;
import com.test.automation.sdk.reporting.AnalyticsTrendReport.TestOutcome;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Classifies a test as "known flaky" using the cross-run history already
 * captured by the analytics event store
 * ({@link com.test.automation.sdk.reporting.AnalyticsExecutionReporter} /
 * {@link AnalyticsTrendReport}), so a genuinely intermittent test can be
 * distinguished from both a stable test (first-time failure = real
 * regression, must not be hidden) and a consistently-broken test (failing
 * almost every run = broken, not flaky, must also not be hidden).
 *
 * <p>A test key (format: {@code className.methodName[testCaseName]}, matching
 * {@link AnalyticsTrendReport#summarizeTestOutcomes(Path)}) is considered
 * known flaky when, across the scanned history:</p>
 * <ol>
 *   <li>it has both passed and failed at least once ({@link TestOutcome#isFlaky()}), and</li>
 *   <li>it has run at least {@code minRunsForQuarantine} times (a single
 *       pass+fail pair is too little evidence), and</li>
 *   <li>its failure rate is at or below {@code maxFailureRatePercent} (a test
 *       failing almost every run is broken, not flaky, and must keep failing
 *       the build normally).</li>
 * </ol>
 *
 * <p>The underlying history is read once and cached for the lifetime of this
 * instance -- a suite run typically creates one registry per JVM and queries
 * it many times as tests complete, so re-reading the whole analytics
 * directory per test would be wasteful.</p>
 */
public class FlakyTestRegistry {

    private final Map<String, TestOutcome> history;
    private final int minRunsForQuarantine;
    private final double maxFailureRatePercent;

    public FlakyTestRegistry(Path analyticsDirectory, int minRunsForQuarantine, double maxFailureRatePercent) {
        this.minRunsForQuarantine = minRunsForQuarantine;
        this.maxFailureRatePercent = maxFailureRatePercent;
        this.history = loadHistory(analyticsDirectory);
    }

    private static Map<String, TestOutcome> loadHistory(Path analyticsDirectory) {
        try {
            return AnalyticsTrendReport.summarizeTestOutcomes(analyticsDirectory);
        } catch (IOException e) {
            return Collections.emptyMap();
        }
    }

    /**
     * @param testKey {@code className.methodName[testCaseName]} -- see
     *                {@link #buildTestKey(String, String, String)}
     * @return {@code true} when the analytics history classifies this test as
     *         known flaky per the rules described in the class javadoc
     */
    public boolean isKnownFlaky(String testKey) {
        if (testKey == null || testKey.isEmpty()) {
            return false;
        }
        TestOutcome outcome = history.get(testKey);
        if (outcome == null || !outcome.isFlaky()) {
            return false;
        }
        if (outcome.getTotalRuns() < minRunsForQuarantine) {
            return false;
        }
        double failureRate = (outcome.getFailCount() * 100.0) / outcome.getTotalRuns();
        return failureRate <= maxFailureRatePercent;
    }

    /** Read-only snapshot of the aggregated history this registry was built from -- primarily for reporting/debugging. */
    public Map<String, TestOutcome> getHistory() {
        return Collections.unmodifiableMap(history);
    }

    /** Builds the same {@code className.methodName[testCaseName]} key format used by {@link AnalyticsTrendReport}. */
    public static String buildTestKey(String className, String methodName, String testCaseName) {
        String safeClassName = className == null ? "" : className;
        String safeMethodName = methodName == null ? "" : methodName;
        if (safeClassName.isEmpty() && safeMethodName.isEmpty()) {
            return "";
        }
        String base = safeClassName + "." + safeMethodName;
        return (testCaseName == null || testCaseName.isEmpty()) ? base : base + "[" + testCaseName + "]";
    }
}
