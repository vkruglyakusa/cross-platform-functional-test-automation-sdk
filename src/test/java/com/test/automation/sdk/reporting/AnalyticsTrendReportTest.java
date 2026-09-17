package com.test.automation.sdk.reporting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AnalyticsTrendReport}. Writes small fixture
 * {@code *.jsonl} files (mirroring what {@link AnalyticsExecutionReporter}
 * would produce across several separate runs) and verifies the cross-run
 * aggregation logic.
 */
@DisplayName("AnalyticsTrendReport -- cross-run test outcome and healing aggregation")
class AnalyticsTrendReportTest {

    @TempDir
    File tempDir;

    private Path writeRunFile(String name, String... lines) throws Exception {
        Path file = tempDir.toPath().resolve(name);
        Files.write(file, String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    @DisplayName("a test that only ever passes across runs is not flaky")
    void consistentlyPassingTestIsNotFlaky() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"LoginTest\",\"methodName\":\"testLogin\"}");
        writeRunFile("run2.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"LoginTest\",\"methodName\":\"testLogin\"}");

        Map<String, AnalyticsTrendReport.TestOutcome> outcomes =
                AnalyticsTrendReport.summarizeTestOutcomes(tempDir.toPath());

        AnalyticsTrendReport.TestOutcome outcome = outcomes.get("LoginTest.testLogin");
        assertNotNull(outcome);
        assertEquals(2, outcome.getPassCount());
        assertEquals(0, outcome.getFailCount());
        assertFalse(outcome.isFlaky());
    }

    @Test
    @DisplayName("a test that both passes and fails across runs is flagged flaky")
    void mixedPassFailTestIsFlaky() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}");
        writeRunFile("run2.jsonl",
                "{\"type\":\"TEST_FAILED\",\"className\":\"CheckoutTest\",\"methodName\":\"testCheckout\"}");

        Map<String, AnalyticsTrendReport.TestOutcome> outcomes =
                AnalyticsTrendReport.summarizeTestOutcomes(tempDir.toPath());

        AnalyticsTrendReport.TestOutcome outcome = outcomes.get("CheckoutTest.testCheckout");
        assertNotNull(outcome);
        assertTrue(outcome.isFlaky());
        assertEquals(1, outcome.getPassCount());
        assertEquals(1, outcome.getFailCount());
        assertEquals(2, outcome.getTotalRuns());
    }

    @Test
    @DisplayName("data-driven rows are distinguished by testCaseName in the aggregation key")
    void dataDrivenRowsAreDistinguishedByTestCaseName() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"TEST_PASSED\",\"className\":\"SearchTest\",\"methodName\":\"testSearch\",\"testCaseName\":\"ADO-1\"}",
                "{\"type\":\"TEST_FAILED\",\"className\":\"SearchTest\",\"methodName\":\"testSearch\",\"testCaseName\":\"ADO-2\"}");

        Map<String, AnalyticsTrendReport.TestOutcome> outcomes =
                AnalyticsTrendReport.summarizeTestOutcomes(tempDir.toPath());

        assertTrue(outcomes.containsKey("SearchTest.testSearch[ADO-1]"));
        assertTrue(outcomes.containsKey("SearchTest.testSearch[ADO-2]"));
        assertFalse(outcomes.get("SearchTest.testSearch[ADO-1]").isFlaky());
        assertFalse(outcomes.get("SearchTest.testSearch[ADO-2]").isFlaky());
    }

    @Test
    @DisplayName("non-outcome events (e.g. STEP_STARTED) are ignored by outcome summarization")
    void nonOutcomeEventsAreIgnored() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"STEP_STARTED\",\"className\":\"LoginTest\",\"methodName\":\"testLogin\"}",
                "{\"type\":\"TEST_PASSED\",\"className\":\"LoginTest\",\"methodName\":\"testLogin\"}");

        Map<String, AnalyticsTrendReport.TestOutcome> outcomes =
                AnalyticsTrendReport.summarizeTestOutcomes(tempDir.toPath());

        assertEquals(1, outcomes.get("LoginTest.testLogin").getTotalRuns());
    }

    @Test
    @DisplayName("healing summary counts healed vs. exhausted attempts per locator")
    void healingSummaryCountsHealedVsExhausted() throws Exception {
        String locator = "By.xpath: //input[@id='email' and @type='text']";
        writeRunFile("run1.jsonl",
                "{\"type\":\"ACTION_COMPLETED\",\"action\":\"LOCATOR_HEALED\",\"locator\":\"" + locator + "\"}",
                "{\"type\":\"ACTION_COMPLETED\",\"action\":\"LOCATOR_HEALED\",\"locator\":\"" + locator + "\"}");
        writeRunFile("run2.jsonl",
                "{\"type\":\"ACTION_FAILED\",\"action\":\"LOCATOR_HEALED\",\"locator\":\"" + locator + "\"}");

        Map<String, AnalyticsTrendReport.HealStats> healing =
                AnalyticsTrendReport.summarizeHealing(tempDir.toPath());

        AnalyticsTrendReport.HealStats stats = healing.get(locator);
        assertNotNull(stats);
        assertEquals(2, stats.getHealedCount());
        assertEquals(1, stats.getExhaustedCount());
        assertEquals(3, stats.getTotalAttempts());
    }

    @Test
    @DisplayName("events without the LOCATOR_HEALED action are ignored by healing summarization")
    void nonHealingActionsAreIgnoredByHealingSummary() throws Exception {
        writeRunFile("run1.jsonl",
                "{\"type\":\"ACTION_COMPLETED\",\"action\":\"CLICK\",\"locator\":\"By.id: submit\"}");

        Map<String, AnalyticsTrendReport.HealStats> healing =
                AnalyticsTrendReport.summarizeHealing(tempDir.toPath());

        assertTrue(healing.isEmpty());
    }

    @Test
    @DisplayName("a malformed line in a run file is skipped without aborting aggregation of the rest")
    void malformedLineIsSkippedNotFatal() throws Exception {
        writeRunFile("run1.jsonl",
                "{ not valid json",
                "{\"type\":\"TEST_PASSED\",\"className\":\"LoginTest\",\"methodName\":\"testLogin\"}");

        Map<String, AnalyticsTrendReport.TestOutcome> outcomes =
                AnalyticsTrendReport.summarizeTestOutcomes(tempDir.toPath());

        assertEquals(1, outcomes.get("LoginTest.testLogin").getPassCount());
    }

    @Test
    @DisplayName("a non-existent analytics directory yields an empty result, never throws")
    void nonExistentDirectoryYieldsEmptyResult() throws Exception {
        Path missing = tempDir.toPath().resolve("does-not-exist");
        assertTrue(AnalyticsTrendReport.summarizeTestOutcomes(missing).isEmpty());
        assertTrue(AnalyticsTrendReport.summarizeHealing(missing).isEmpty());
    }
}
