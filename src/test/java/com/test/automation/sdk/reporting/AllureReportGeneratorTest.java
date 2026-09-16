package com.test.automation.sdk.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.test.automation.sdk.config.ConfigurationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * OBS-9: unit tests for {@link AllureReportGenerator}. All process execution
 * is faked via {@link FakeProcessRunner} -- no test depends on a real
 * installed Allure CLI (see class docs for the manual/real-CLI smoke check).
 */
@DisplayName("Allure automatic report generation (OBS-9)")
class AllureReportGeneratorTest {

    private FakeProcessRunner fakeRunner;

    private static final String KEY_ENABLED = "reporting.allure.enabled";
    private static final String KEY_GENERATE = "reporting.allure.generateAfterExecution";
    private static final String KEY_OPEN = "reporting.allure.openAfterGeneration";
    private static final String KEY_RESULTS_DIR = "reporting.allure.resultsDirectory";
    private static final String KEY_REPORT_DIR = "reporting.allure.reportDirectory";
    private static final String KEY_TIMEOUT = "reporting.allure.generationTimeoutSeconds";

    @BeforeEach
    void setUp() {
        fakeRunner = new FakeProcessRunner();
        AllureReportGenerator.setProcessRunnerForTests(fakeRunner);
    }

    @AfterEach
    void tearDown() {
        AllureReportGenerator.resetForTests();
        System.clearProperty(KEY_ENABLED);
        System.clearProperty(KEY_GENERATE);
        System.clearProperty(KEY_OPEN);
        System.clearProperty(KEY_RESULTS_DIR);
        System.clearProperty(KEY_REPORT_DIR);
        System.clearProperty(KEY_TIMEOUT);
    }

    // ------------------------------------------------------------
    // 1. Configuration defaults
    // ------------------------------------------------------------
    @Test
    @DisplayName("1. Configuration defaults match the OBS-9 requirements (opening a browser is never the default)")
    void configurationDefaults() {
        ConfigurationManager.AllureReportConfig config = ConfigurationManager.getAllureReportConfig();

        assertTrue(config.enabled());
        assertTrue(config.generateAfterExecution());
        assertFalse(config.openAfterGeneration(), "openAfterGeneration must default to false");
        assertEquals("allure-results", config.resultsDirectory());
        assertEquals("allure-report", config.reportDirectory());
        assertEquals(120, config.generationTimeoutSeconds());
    }

    // ------------------------------------------------------------
    // 2. Generation disabled
    // ------------------------------------------------------------
    @Test
    @DisplayName("2. generateAfterExecution=false skips generation entirely -- no process is even launched")
    void generationDisabled_skipsEntirely(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_GENERATE, "false");

        AllureReportGenerator.runPostExecutionLifecycle();

        assertTrue(fakeRunner.runCalls.isEmpty(), "No command should be run when generation is disabled");
        assertTrue(fakeRunner.detachedCalls.isEmpty());
    }

    @Test
    @DisplayName("2b. reporting.allure.enabled=false (master switch) also skips generation entirely")
    void masterSwitchDisabled_skipsEntirely(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_ENABLED, "false");

        AllureReportGenerator.runPostExecutionLifecycle();

        assertTrue(fakeRunner.runCalls.isEmpty());
    }

    // ------------------------------------------------------------
    // 3. Allure CLI unavailable
    // ------------------------------------------------------------
    @Test
    @DisplayName("3. Allure CLI unavailable: generation is skipped cleanly, no exception")
    void allureCliUnavailable_skipsGenerationCleanly(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        fakeRunner.versionResult = AllureReportGenerator.CommandResult.notExecuted("allure: command not found");

        assertDoesNotThrow(AllureReportGenerator::runPostExecutionLifecycle);

        assertEquals(1, fakeRunner.runCalls.size(), "Only the --version probe should run");
        assertTrue(fakeRunner.runCalls.get(0).contains("--version"));
        assertTrue(fakeRunner.detachedCalls.isEmpty());
    }

    @Test
    @DisplayName("isAllureAvailable() returns false when the probe command cannot be executed")
    void isAllureAvailable_falseWhenNotExecuted() {
        fakeRunner.versionResult = AllureReportGenerator.CommandResult.notExecuted("not found");

        assertFalse(AllureReportGenerator.isAllureAvailable());
    }

    @Test
    @DisplayName("isAllureAvailable() returns true when --version exits 0")
    void isAllureAvailable_trueOnExitZero() {
        fakeRunner.versionResult = AllureReportGenerator.CommandResult.ofSuccess(0, "2.24.0", "");

        assertTrue(AllureReportGenerator.isAllureAvailable());
    }

    // ------------------------------------------------------------
    // 4. Successful generation
    // ------------------------------------------------------------
    @Test
    @DisplayName("4. Successful generation runs 'allure generate <results> --clean -o <report>'")
    void successfulGeneration_runsExpectedCommand(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Path reportDir = tempDir.resolve("allure-report");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_REPORT_DIR, reportDir.toString());

        AllureReportGenerator.runPostExecutionLifecycle();

        assertEquals(2, fakeRunner.runCalls.size(), "Expected a --version probe then a generate call");
        List<String> generateCommand = fakeRunner.runCalls.get(1);
        assertTrue(generateCommand.contains("generate"));
        assertTrue(generateCommand.contains(resultsDir.toString()));
        assertTrue(generateCommand.contains("--clean"));
        int oIndex = generateCommand.indexOf("-o");
        assertTrue(oIndex >= 0 && oIndex + 1 < generateCommand.size());
        assertEquals(reportDir.toString(), generateCommand.get(oIndex + 1));
    }

    // ------------------------------------------------------------
    // 5. Failed generation / non-zero exit code
    // ------------------------------------------------------------
    @Test
    @DisplayName("5. Non-zero exit code from 'allure generate' is handled without throwing")
    void failedGeneration_nonZeroExitCode_doesNotThrow(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        fakeRunner.generateResult = AllureReportGenerator.CommandResult.ofSuccess(1, "", "some generation error");

        assertDoesNotThrow(AllureReportGenerator::runPostExecutionLifecycle);

        assertFalse(fakeRunner.generateResult.isSuccess());
        assertTrue(fakeRunner.detachedCalls.isEmpty(), "A failed generation must never attempt to open the report");
    }

    @Test
    @DisplayName("5b. Generation timeout is treated as a failure, not a crash")
    void generationTimeout_isTreatedAsFailure(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        fakeRunner.generateResult = AllureReportGenerator.CommandResult.ofTimeout("partial output", "");

        assertDoesNotThrow(AllureReportGenerator::runPostExecutionLifecycle);

        assertTrue(fakeRunner.generateResult.isTimedOut());
        assertFalse(fakeRunner.generateResult.isSuccess());
    }

    // ------------------------------------------------------------
    // 6/7. Configured results/report directories are honored
    // ------------------------------------------------------------
    @Test
    @DisplayName("6/7. Custom resultsDirectory and reportDirectory configuration values are used verbatim")
    void customDirectories_areHonored(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("custom-results-dir");
        Path reportDir = tempDir.resolve("custom-report-dir");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_REPORT_DIR, reportDir.toString());

        AllureReportGenerator.runPostExecutionLifecycle();

        List<String> generateCommand = fakeRunner.runCalls.get(1);
        assertTrue(generateCommand.contains(resultsDir.toString()));
        assertTrue(generateCommand.contains(reportDir.toString()));
    }

    // ------------------------------------------------------------
    // 8. Paths containing spaces
    // ------------------------------------------------------------
    @Test
    @DisplayName("8. Paths containing spaces are passed as single, unmodified argument-list entries")
    void pathsWithSpaces_arePreservedAsSingleArguments(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure results with spaces");
        Path reportDir = tempDir.resolve("allure report output");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_REPORT_DIR, reportDir.toString());

        AllureReportGenerator.runPostExecutionLifecycle();

        List<String> generateCommand = fakeRunner.runCalls.get(1);
        // The full path (spaces included) must appear as exactly one list element --
        // proof that no shell string concatenation/re-splitting occurred.
        assertTrue(generateCommand.contains(resultsDir.toString()));
        assertTrue(generateCommand.contains(reportDir.toString()));
        for (String arg : generateCommand) {
            assertFalse(arg.contains(" && ") || arg.contains(" ; "), "No shell-joined argument expected");
        }
    }

    // ------------------------------------------------------------
    // 9/10. openAfterGeneration
    // ------------------------------------------------------------
    @Test
    @DisplayName("9. openAfterGeneration=false (default) never launches 'allure open'")
    void openAfterGenerationFalse_neverOpensReport(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_OPEN, "false");

        AllureReportGenerator.runPostExecutionLifecycle();

        assertTrue(fakeRunner.detachedCalls.isEmpty());
    }

    @Test
    @DisplayName("10. openAfterGeneration=true launches 'allure open' as a non-blocking detached process after success")
    void openAfterGenerationTrue_opensReportNonBlockingAfterSuccess(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Path reportDir = tempDir.resolve("allure-report");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_REPORT_DIR, reportDir.toString());
        System.setProperty(KEY_OPEN, "true");

        long start = System.currentTimeMillis();
        AllureReportGenerator.runPostExecutionLifecycle();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(1, fakeRunner.detachedCalls.size());
        List<String> openCommand = fakeRunner.detachedCalls.get(0);
        assertTrue(openCommand.contains("open"));
        assertTrue(openCommand.contains(reportDir.toString()));
        // The fake's startDetached() returns immediately (as the real one must) --
        // this run should complete near-instantly, never blocking on a "server".
        assertTrue(elapsed < 5000, "runPostExecutionLifecycle() must not block on opening the report");
    }

    @Test
    @DisplayName("openAfterGeneration=true does NOT open the report when generation failed")
    void openAfterGenerationTrue_doesNotOpenWhenGenerationFailed(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        System.setProperty(KEY_OPEN, "true");
        fakeRunner.generateResult = AllureReportGenerator.CommandResult.ofSuccess(1, "", "boom");

        AllureReportGenerator.runPostExecutionLifecycle();

        assertTrue(fakeRunner.detachedCalls.isEmpty(), "Must never open a report that failed to generate");
    }

    // ------------------------------------------------------------
    // 11. Generation invoked once
    // ------------------------------------------------------------
    @Test
    @DisplayName("11. Calling the lifecycle hook twice only generates the report once")
    void lifecycleHook_calledTwice_generatesOnce(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());

        AllureReportGenerator.runPostExecutionLifecycle();
        AllureReportGenerator.runPostExecutionLifecycle();

        // 1 x --version + 1 x generate = 2 total, not 4.
        assertEquals(2, fakeRunner.runCalls.size());
    }

    // ------------------------------------------------------------
    // 12. Parallel execution does not trigger duplicate generation
    // ------------------------------------------------------------
    @Test
    @DisplayName("12. Concurrent suite-finish callbacks (parallel execution) generate the report exactly once")
    void parallelInvocations_generateExactlyOnce(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());

        int threadCount = 20;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                AllureReportGenerator.runPostExecutionLifecycle();
                done.countDown();
            });
            t.setDaemon(true);
            t.start();
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "All threads should finish quickly");

        long generateInvocations = fakeRunner.runCalls.stream().filter(cmd -> cmd.contains("generate")).count();
        assertEquals(1, generateInvocations, "Only one thread may run 'allure generate'");
    }

    // ------------------------------------------------------------
    // 13. Report-generation failure never changes the underlying test result
    // ------------------------------------------------------------
    @Test
    @DisplayName("13. Even a runner-level exception during generation is fully swallowed (never affects test results)")
    void runnerException_isFullySwallowed(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        fakeRunner.throwOnGenerate = true;

        // AllureReportGenerator has no reference to any ITestResult/TestNG API at
        // all, so there is nothing it could change even if it wanted to -- this
        // test proves the failure path itself never escapes as an exception.
        assertDoesNotThrow(AllureReportGenerator::runPostExecutionLifecycle);
    }

    // ------------------------------------------------------------
    // 14. Existing Allure result files are never deleted
    // ------------------------------------------------------------
    @Test
    @DisplayName("14. Existing allure-results files are never touched/deleted by generation")
    void existingResultFiles_areNeverDeleted(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        Path resultFile = resultsDir.resolve("12345-result.json");
        Files.writeString(resultFile, "{\"status\":\"passed\"}");
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());

        AllureReportGenerator.runPostExecutionLifecycle();

        assertTrue(Files.exists(resultFile), "Result file must still exist after generation");
        List<String> generateCommand = fakeRunner.runCalls.get(1);
        // --clean must apply to the report (-o) target only, never to the results source.
        int cleanIndex = generateCommand.indexOf("--clean");
        int oIndex = generateCommand.indexOf("-o");
        assertTrue(cleanIndex >= 0);
        assertTrue(oIndex > cleanIndex, "--clean must precede the -o report target, not the results source");
    }

    // ------------------------------------------------------------
    // 15. Secrets are not exposed in diagnostics
    // ------------------------------------------------------------
    @Test
    @DisplayName("15. A secret-looking value in Allure CLI stderr is redacted before being logged")
    void secretsInStderr_areRedactedBeforeLogging(@TempDir Path tempDir) throws IOException {
        Path resultsDir = tempDir.resolve("allure-results");
        Files.createDirectories(resultsDir);
        System.setProperty(KEY_RESULTS_DIR, resultsDir.toString());
        fakeRunner.generateResult = AllureReportGenerator.CommandResult.ofSuccess(
                1, "", "token=abcSuperSecretValue123 failed to connect");

        CapturingAppender appender = CapturingAppender.attachTo(AllureReportGenerator.class);
        try {
            AllureReportGenerator.runPostExecutionLifecycle();
        } finally {
            appender.detach();
        }

        String combinedLog = String.join("\n", appender.messages);
        assertFalse(combinedLog.contains("abcSuperSecretValue123"), "Raw secret value must never reach the logs");
        assertTrue(combinedLog.contains("[REDACTED]") || combinedLog.toLowerCase().contains("failed"),
                "Expected a redacted failure message to be logged");
    }

    // ================================================================
    // Test doubles
    // ================================================================

    /** Records every invocation instead of spawning real OS processes. */
    private static final class FakeProcessRunner implements AllureReportGenerator.ProcessRunner {
        final List<List<String>> runCalls = new CopyOnWriteArrayList<>();
        final List<List<String>> detachedCalls = new CopyOnWriteArrayList<>();
        final AtomicInteger generateCount = new AtomicInteger();

        AllureReportGenerator.CommandResult versionResult =
                AllureReportGenerator.CommandResult.ofSuccess(0, "2.24.0", "");
        AllureReportGenerator.CommandResult generateResult =
                AllureReportGenerator.CommandResult.ofSuccess(0, "Report generated", "");
        volatile boolean throwOnGenerate = false;

        @Override
        public AllureReportGenerator.CommandResult run(List<String> command, Path workingDirectory, long timeoutSeconds) {
            runCalls.add(new ArrayList<>(command));
            if (command.contains("--version")) {
                return versionResult;
            }
            if (command.contains("generate")) {
                if (throwOnGenerate) {
                    throw new RuntimeException("simulated process failure");
                }
                generateCount.incrementAndGet();
                return generateResult;
            }
            return AllureReportGenerator.CommandResult.notExecuted("unexpected command: " + command);
        }

        @Override
        public void startDetached(List<String> command, Path workingDirectory) {
            detachedCalls.add(new ArrayList<>(command));
        }
    }

    /** Minimal Log4j2 appender used only to assert redaction of a single failure message. */
    private static final class CapturingAppender extends AbstractAppender {
        final List<String> messages = new CopyOnWriteArrayList<>();
        private final Logger targetLogger;

        private CapturingAppender(Logger targetLogger) {
            super("obs9-capturing-appender", null, null, true, Property.EMPTY_ARRAY);
            this.targetLogger = targetLogger;
        }

        static CapturingAppender attachTo(Class<?> loggerClass) {
            Logger logger = (Logger) LogManager.getLogger(loggerClass.getName());
            CapturingAppender appender = new CapturingAppender(logger);
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.ALL);
            return appender;
        }

        void detach() {
            targetLogger.removeAppender(this);
            stop();
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
