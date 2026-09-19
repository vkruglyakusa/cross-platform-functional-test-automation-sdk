package com.test.automation.sdk.reporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.test.automation.sdk.config.ConfigurationManager;

/**
 * OBS-9: automatic Allure HTML report generation after test execution.
 *
 * <p>Small, self-contained post-execution service -- deliberately kept out of
 * {@link com.test.automation.sdk.listener.Listener} and
 * {@link com.test.automation.sdk.testbase.TestBase} (which coordinate
 * lifecycle only, per the SDK's existing separation of concerns) and out of
 * {@link ExecutionReporting}/{@link ExecutionReporter} (which model
 * per-test/per-step business events, not a once-per-run OS process
 * invocation). Called exactly once, from {@code Listener.onFinish(ISuite)},
 * after all Allure result files for the run have already been written by the
 * normal TestNG/Allure listener lifecycle.</p>
 *
 * <p>Configuration is resolved entirely through the SDK's existing
 * {@link ConfigurationManager} precedence chain (system property &gt;
 * environment variable &gt; {@code sdk-config.yaml} &gt; default) via
 * {@link ConfigurationManager.AllureReportConfig} -- see
 * {@code sdk-config.yaml.template}'s {@code reporting.allure.*} keys.</p>
 *
 * <h2>Failure semantics</h2>
 * This class never throws out of {@link #runPostExecutionLifecycle()} and
 * never touches the TestNG/Maven test result. Allure CLI absence, a failed
 * {@code allure generate} invocation, or a generation timeout are all logged
 * and swallowed -- report generation is a best-effort convenience, not a test
 * outcome.
 */
public final class AllureReportGenerator {

    private static final Logger log = LogManager.getLogger(AllureReportGenerator.class.getName());

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /** Ensures the whole generate/open sequence runs at most once per JVM run. */
    private static final AtomicBoolean hasRun = new AtomicBoolean(false);

    private static ProcessRunner runner = new RealProcessRunner();

    private AllureReportGenerator() {
    }

    /**
     * Suite-finish entry point. Safe to call more than once (e.g. multiple
     * {@code <suite>} blocks in one Maven run) -- only the first invocation
     * does any work; later calls are logged at DEBUG and return immediately.
     */
    public static void runPostExecutionLifecycle() {
        if (!hasRun.compareAndSet(false, true)) {
            log.debug("[Allure] Post-execution report generation already handled for this run -- skipping duplicate invocation.");
            return;
        }
        try {
            runOnce();
        } catch (RuntimeException e) {
            // Belt-and-braces: a report-generation problem must never surface as
            // (or be mistaken for) a test-execution failure.
            log.warn("[Allure] Automatic report generation skipped due to an unexpected error: {}",
                    SecretRedactor.redactMessage(String.valueOf(e.getMessage())));
        }
    }

    private static void runOnce() {
        ConfigurationManager.AllureReportConfig config = ConfigurationManager.getAllureReportConfig();
        if (!config.enabled() || !config.generateAfterExecution()) {
            log.info("[Allure] Automatic report generation disabled (reporting.allure.enabled={}, reporting.allure.generateAfterExecution={}) -- skipping.",
                    config.enabled(), config.generateAfterExecution());
            return;
        }

        Path resultsDir = Path.of(config.resultsDirectory());
        Path reportDir = Path.of(config.reportDirectory());

        if (!Files.isDirectory(resultsDir)) {
            log.info("[Allure] Report generation skipped: results directory '{}' does not exist (no Allure results were produced this run).",
                    resultsDir);
            return;
        }

        if (!isAllureAvailable()) {
            log.warn("Allure report generation skipped: Allure CLI was not found on the execution machine. "
                    + "Install the Allure commandline (see SDK-USER-GUIDE.md, 'Allure Reporting') to enable automatic HTML report generation.");
            return;
        }

        log.info("[Allure] Report generation started (results='{}', report='{}')...", resultsDir, reportDir);
        CommandResult result = generateReport(resultsDir, reportDir, config.generationTimeoutSeconds());

        if (result.isSuccess()) {
            log.info("Allure HTML report generated successfully: {}", reportDir.toAbsolutePath());
            if (config.openAfterGeneration()) {
                openReportNonBlocking(reportDir);
            } else {
                log.debug("[Allure] reporting.allure.openAfterGeneration=false -- not opening the report automatically.");
            }
        } else if (result.isTimedOut()) {
            log.warn("[Allure] Report generation timed out after {}s (results='{}', report='{}'). "
                    + "No report was generated; increase reporting.allure.generationTimeoutSeconds if this recurs.",
                    config.generationTimeoutSeconds(), resultsDir, reportDir);
        } else {
            log.warn("[Allure] Report generation failed (exitCode={}). stderr: {}",
                    result.getExitCode(), SecretRedactor.redactMessage(truncate(result.getStderr())));
        }
    }

    /**
     * Checks whether the Allure commandline is available by running
     * {@code allure --version} with a short timeout. Never throws --
     * "not found", "not executable", and "timed out" are all treated as
     * unavailable.
     */
    public static boolean isAllureAvailable() {
        CommandResult result = runner.run(buildCommand("--version"), Path.of("."), 10L);
        return result.isExecuted() && result.getExitCode() == 0;
    }

    /**
     * Runs {@code allure generate <resultsDir> --clean -o <reportDir>}.
     * {@code --clean} applies only to {@code reportDir} (the generated HTML
     * output) -- {@code resultsDir} (the raw execution evidence) is never
     * written to or deleted by this method.
     */
    static CommandResult generateReport(Path resultsDir, Path reportDir, long timeoutSeconds) {
        List<String> command = buildCommand("generate", resultsDir.toString(), "--clean", "-o", reportDir.toString());
        return runner.run(command, Path.of("."), timeoutSeconds);
    }

    /**
     * Opens the generated report. {@code allure open} starts a small local
     * web server and keeps running until interrupted -- waiting for it to
     * exit would hang the Maven/TestNG process forever, so this launches it
     * as a detached, fire-and-forget process and returns immediately without
     * waiting for (or reporting on) its exit code.
     */
    static void openReportNonBlocking(Path reportDir) {
        try {
            runner.startDetached(buildCommand("open", reportDir.toString()), Path.of("."));
            log.info("[Allure] Opening report in default browser (non-blocking): {}", reportDir.toAbsolutePath());
        } catch (RuntimeException e) {
            log.warn("[Allure] Could not open the generated report automatically: {}",
                    SecretRedactor.redactMessage(String.valueOf(e.getMessage())));
        }
    }

    /**
     * Builds the OS-appropriate argument list for an Allure CLI invocation.
     *
     * <p>On Windows, the Allure commandline is typically installed as an
     * {@code allure.bat}/{@code allure.cmd} shim (npm/Scoop/zip-distribution
     * installs all produce this). Windows' underlying {@code CreateProcess}
     * API -- which {@link ProcessBuilder} calls directly -- cannot launch a
     * batch file as an executable image; only {@code cmd.exe} can. This is a
     * well-known, demonstrated JDK-on-Windows limitation (not a generic
     * preference for shelling out), so {@code cmd.exe /c} is used only on
     * Windows. Every argument remains a separate list element passed to
     * {@link ProcessBuilder} -- nothing is concatenated into a single shell
     * string, so paths containing spaces and any shell metacharacters are
     * never re-interpreted by a shell.</p>
     */
    private static List<String> buildCommand(String... allureArgs) {
        List<String> command = new ArrayList<>();
        if (WINDOWS) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add("allure");
        Collections.addAll(command, allureArgs);
        return command;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        int max = 2000;
        return value.length() > max ? value.substring(0, max) + "... [truncated]" : value;
    }

    // ------------------------------------------------------------------
    // Test seams -- package-private, not part of the public API surface.
    // ------------------------------------------------------------------

    static void setProcessRunnerForTests(ProcessRunner testRunner) {
        runner = testRunner;
    }

    static void resetForTests() {
        runner = new RealProcessRunner();
        hasRun.set(false);
    }

    /**
     * Minimal process-execution seam. Deliberately just wide enough to make
     * {@link AllureReportGenerator} deterministically testable without
     * spawning a real Allure CLI process -- intentionally not a general
     * command-execution framework.
     */
    interface ProcessRunner {
        /** Runs {@code command} to completion (or until {@code timeoutSeconds} elapses). */
        CommandResult run(List<String> command, Path workingDirectory, long timeoutSeconds);

        /** Launches {@code command} and returns immediately without waiting for it to exit. */
        void startDetached(List<String> command, Path workingDirectory);
    }

    /** Outcome of a single process execution. */
    static final class CommandResult {
        private final boolean executed;
        private final int exitCode;
        private final boolean timedOut;
        private final String stdout;
        private final String stderr;

        private CommandResult(boolean executed, int exitCode, boolean timedOut, String stdout, String stderr) {
            this.executed = executed;
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        static CommandResult ofSuccess(int exitCode, String stdout, String stderr) {
            return new CommandResult(true, exitCode, false, stdout, stderr);
        }

        static CommandResult ofTimeout(String stdout, String stderr) {
            return new CommandResult(true, -1, true, stdout, stderr);
        }

        static CommandResult notExecuted(String reason) {
            return new CommandResult(false, -1, false, "", reason);
        }

        boolean isExecuted() {
            return executed;
        }

        int getExitCode() {
            return exitCode;
        }

        boolean isTimedOut() {
            return timedOut;
        }

        boolean isSuccess() {
            return executed && !timedOut && exitCode == 0;
        }

        String getStdout() {
            return stdout;
        }

        String getStderr() {
            return stderr;
        }
    }

    /** Real {@link ProcessRunner} backed by {@link ProcessBuilder}. */
    private static final class RealProcessRunner implements ProcessRunner {

        @Override
        public CommandResult run(List<String> command, Path workingDirectory, long timeoutSeconds) {
            Process process = null;
            try {
                ProcessBuilder builder = new ProcessBuilder(command)
                        .directory(workingDirectory.toAbsolutePath().toFile());
                process = builder.start();
                // Drain stdout/stderr concurrently -- required to avoid the classic
                // ProcessBuilder deadlock where the child blocks writing to a full
                // pipe buffer that nothing is reading from yet.
                StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream());
                StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream());
                Thread stdoutThread = new Thread(stdoutGobbler, "allure-stdout-reader");
                Thread stderrThread = new Thread(stderrGobbler, "allure-stderr-reader");
                stdoutThread.setDaemon(true);
                stderrThread.setDaemon(true);
                stdoutThread.start();
                stderrThread.start();

                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                stdoutThread.join(TimeUnit.SECONDS.toMillis(5));
                stderrThread.join(TimeUnit.SECONDS.toMillis(5));

                if (!finished) {
                    process.destroyForcibly();
                    return CommandResult.ofTimeout(stdoutGobbler.getContent(), stderrGobbler.getContent());
                }
                return CommandResult.ofSuccess(process.exitValue(), stdoutGobbler.getContent(), stderrGobbler.getContent());
            } catch (IOException e) {
                // Command not found / not executable on this machine.
                return CommandResult.notExecuted(e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (process != null) {
                    process.destroyForcibly();
                }
                return CommandResult.notExecuted("Interrupted while waiting for process: " + e.getMessage());
            }
        }

        @Override
        public void startDetached(List<String> command, Path workingDirectory) {
            try {
                new ProcessBuilder(command)
                        .directory(workingDirectory.toAbsolutePath().toFile())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                // Deliberately not waited on -- "allure open" runs a long-lived local
                // server; this call must return immediately so the JVM/Maven build
                // is never blocked by it.
            } catch (IOException e) {
                throw new IllegalStateException("Failed to launch detached process: " + e.getMessage(), e);
            }
        }

        /** Reads an InputStream to completion on a background thread. */
        private static final class StreamGobbler implements Runnable {
            private final InputStream input;
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            private StreamGobbler(InputStream input) {
                this.input = input;
            }

            @Override
            public void run() {
                byte[] chunk = new byte[4096];
                int n;
                try {
                    while ((n = input.read(chunk)) != -1) {
                        buffer.write(chunk, 0, n);
                    }
                } catch (IOException ignored) {
                    // Stream closed because the process exited/was destroyed -- not an error.
                }
            }

            String getContent() {
                return buffer.toString();
            }
        }
    }
}
