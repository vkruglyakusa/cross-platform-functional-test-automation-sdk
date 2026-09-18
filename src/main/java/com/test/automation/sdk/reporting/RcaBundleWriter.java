package com.test.automation.sdk.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.test.automation.sdk.config.ConfigurationManager;

/**
 * Tier 3 (#9) -- automated RCA-to-fix loop.
 *
 * <p>On every test failure, {@link ExecutionReporting#onTestFailed} calls
 * {@link #write(Context)}, which consolidates everything a human (or Copilot,
 * via {@code fix-failed-test.prompt.md}) needs to diagnose the failure into
 * ONE JSON file: test/suite identity, the exception chain, paths to the
 * already-captured screenshot/DOM-dump evidence, and -- critically -- a
 * pre-fetched tail of the shared SDK log file, so the reader never has to
 * hunt down three separate artifacts (screenshot, DOM dump, log) by hand
 * before starting root-cause analysis. This directly implements the
 * "screenshot + DOM + log together" rule already mandated by
 * {@code failure-investigation.instructions.md} / {@code test-fix.instructions.md},
 * just pre-assembled at the moment of failure while the log tail is still
 * fresh and cheap to read.</p>
 *
 * <p>Never throws and never blocks test execution longer than a best-effort
 * file write: any failure while building or writing the bundle is logged and
 * swallowed, mirroring {@link AnalyticsExecutionReporter}'s
 * write-failures-never-fail-a-test philosophy. One JSON file per failure
 * (not one shared file) so bundles can be diffed, attached to a PR, or fed to
 * Copilot individually.</p>
 */
public final class RcaBundleWriter {

    private static final Logger log = LogManager.getLogger(RcaBundleWriter.class);
    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private RcaBundleWriter() {
    }

    /** Immutable snapshot of everything one failure bundle needs, gathered by {@link ExecutionReporting}. */
    public static final class Context {
        private String testCaseName = "";
        private String className = "";
        private String methodName = "";
        private String suiteName = "";
        private String testNgTestName = "";
        private String platform = "";
        private String browser = "";
        private String device = "";
        private String lastCompletedStep = "";
        private Throwable throwable;
        private List<ExecutionEvidence> evidence = new ArrayList<ExecutionEvidence>();

        public Context testCaseName(String value) {
            this.testCaseName = value == null ? "" : value;
            return this;
        }

        public Context className(String value) {
            this.className = value == null ? "" : value;
            return this;
        }

        public Context methodName(String value) {
            this.methodName = value == null ? "" : value;
            return this;
        }

        public Context suiteName(String value) {
            this.suiteName = value == null ? "" : value;
            return this;
        }

        public Context testNgTestName(String value) {
            this.testNgTestName = value == null ? "" : value;
            return this;
        }

        public Context platform(String value) {
            this.platform = value == null ? "" : value;
            return this;
        }

        public Context browser(String value) {
            this.browser = value == null ? "" : value;
            return this;
        }

        public Context device(String value) {
            this.device = value == null ? "" : value;
            return this;
        }

        public Context lastCompletedStep(String value) {
            this.lastCompletedStep = value == null ? "" : value;
            return this;
        }

        public Context throwable(Throwable value) {
            this.throwable = value;
            return this;
        }

        public Context evidence(List<ExecutionEvidence> value) {
            this.evidence = value == null ? new ArrayList<ExecutionEvidence>() : value;
            return this;
        }
    }

    public static Context builder() {
        return new Context();
    }

    /**
     * Builds and writes one RCA bundle JSON file for {@code context}. Returns the
     * written file path, or {@code null} if the feature is disabled or the write
     * failed (a failure here never affects test execution).
     */
    public static Path write(Context context) {
        if (context == null || !ConfigurationManager.getRcaBundleConfig().enabled()) {
            return null;
        }
        try {
            JSONObject bundle = toJson(context);
            Path dir = Paths.get(ConfigurationManager.getRcaBundleConfig().directory());
            Files.createDirectories(dir);
            String safeName = safeFileToken(
                    !context.testCaseName.isEmpty() ? context.testCaseName
                            : (!context.methodName.isEmpty() ? context.methodName : "test"));
            Path file = dir.resolve(safeName + "_" + FILE_STAMP.format(Instant.now()) + ".json");
            Files.write(file, bundle.toString(2).getBytes(StandardCharsets.UTF_8));
            return file;
        } catch (Exception e) {
            log.warn("RcaBundleWriter: failed to write RCA bundle: " + e.getMessage());
            return null;
        }
    }

    private static JSONObject toJson(Context context) {
        JSONObject json = new JSONObject();
        json.put("schemaVersion", 1);
        json.put("generatedAtIso", Instant.now().toString());
        json.put("status", "FAILED");

        JSONObject test = new JSONObject();
        putIfNotEmpty(test, "testCaseName", context.testCaseName);
        putIfNotEmpty(test, "className", context.className);
        putIfNotEmpty(test, "methodName", context.methodName);
        putIfNotEmpty(test, "suiteName", context.suiteName);
        putIfNotEmpty(test, "testNgTestName", context.testNgTestName);
        putIfNotEmpty(test, "platform", context.platform);
        putIfNotEmpty(test, "browser", context.browser);
        putIfNotEmpty(test, "device", context.device);
        putIfNotEmpty(test, "environment", System.getProperty("environment", ""));
        putIfNotEmpty(test, "lastCompletedStep", context.lastCompletedStep);
        json.put("test", test);

        json.put("exception", exceptionChainToJson(context.throwable));
        json.put("evidence", evidenceToJson(context.evidence));
        json.put("recentLogLines", tailSdkLog());

        // SDK v1.5.1 -- additive, optional convenience references to the new evidence
        // kinds, alongside the existing generic "evidence" array. Purely additive: a
        // bundle consumer that only understands screenshot/DOM/log continues to work
        // unchanged, since these fields are simply absent when no such evidence exists.
        putIfNotEmpty(json, "browserConsoleLog", findEvidencePath(context.evidence, "browserConsole"));
        putIfNotEmpty(json, "networkTrace", findEvidencePath(context.evidence, "networkTrace"));

        JSONArray steps = new JSONArray();
        steps.put("Open the screenshot referenced under evidence[type=screenshot] -- what did the browser show?");
        steps.put("Open the DOM dump referenced under evidence[type=dom] -- is the expected element present with the expected attributes?");
        steps.put("Review recentLogLines above (or the full log at reporting.rcaBundle logsDir) for the last action before failure.");
        steps.put("Cross-reference all three before proposing a fix -- see failure-investigation.instructions.md.");
        json.put("suggestedNextSteps", steps);
        return json;
    }

    private static JSONObject exceptionChainToJson(Throwable throwable) {
        JSONObject json = new JSONObject();
        if (throwable == null) {
            return json;
        }
        int maxFrames = ConfigurationManager.getRcaBundleConfig().stackTraceFrames();
        JSONArray chain = new JSONArray();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            JSONObject node = new JSONObject();
            node.put("type", current.getClass().getName());
            putIfNotEmpty(node, "message", current.getMessage());
            node.put("stackTrace", topFrames(current, maxFrames));
            chain.put(node);
            current = (current.getCause() == current) ? null : current.getCause();
            depth++;
        }
        json.put("chain", chain);
        return json;
    }

    private static JSONArray topFrames(Throwable throwable, int maxFrames) {
        JSONArray frames = new JSONArray();
        StackTraceElement[] elements = throwable.getStackTrace();
        int limit = Math.min(maxFrames, elements.length);
        for (int i = 0; i < limit; i++) {
            frames.put(elements[i].toString());
        }
        return frames;
    }

    private static JSONArray evidenceToJson(List<ExecutionEvidence> evidence) {
        JSONArray array = new JSONArray();
        if (evidence == null) {
            return array;
        }
        for (ExecutionEvidence item : evidence) {
            if (item == null || item.getPath() == null) {
                continue;
            }
            JSONObject node = new JSONObject();
            node.put("type", item.getType());
            node.put("name", item.getName());
            node.put("path", item.getPath().toString());
            array.put(node);
        }
        return array;
    }

    private static JSONArray tailSdkLog() {
        JSONArray lines = new JSONArray();
        try {
            int tailLines = ConfigurationManager.getRcaBundleConfig().logTailLines();
            if (tailLines <= 0) {
                return lines;
            }
            Path logFile = Paths.get(ConfigurationManager.getCommonConfig().logsDir(), "sdk.log");
            if (!Files.isReadable(logFile)) {
                return lines;
            }
            List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - tailLines);
            for (int i = from; i < all.size(); i++) {
                lines.put(all.get(i));
            }
        } catch (IOException e) {
            log.debug("RcaBundleWriter: could not tail sdk.log: " + e.getMessage());
        }
        return lines;
    }

    /** Returns the file path of the first evidence item of {@code evidenceType}, or {@code null}. */
    private static String findEvidencePath(List<ExecutionEvidence> evidence, String evidenceType) {
        if (evidence == null) {
            return null;
        }
        for (ExecutionEvidence item : evidence) {
            if (item != null && item.getPath() != null && evidenceType.equalsIgnoreCase(item.getType())) {
                return item.getPath().toString();
            }
        }
        return null;
    }

    private static void putIfNotEmpty(JSONObject json, String key, String value) {
        if (value != null && !value.isEmpty()) {
            json.put(key, value);
        }
    }

    private static String safeFileToken(String value) {
        String token = value.replaceAll("[^a-zA-Z0-9_-]", "_");
        return token.isEmpty() ? "test" : token;
    }
}
