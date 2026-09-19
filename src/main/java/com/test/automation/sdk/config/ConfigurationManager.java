package com.test.automation.sdk.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Single configuration-resolution engine for the SDK.
 *
 * <p><b>Unified SDK Review Priority 2</b>
 * (docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md,
 * section 8) -- resolution logic must be implemented once. Before this class,
 * {@code System}/env precedence was only honored by the (deliberately
 * self-contained) accessibility config reader; {@link YamlConfigReader} and
 * {@link com.test.automation.sdk.mobile.config.MobileConfigReader} read their
 * flat maps directly with no system-property/env override at all. This class
 * is now the one place that implements the full precedence chain:</p>
 *
 * <pre>
 *   System / Maven property (-Dkey=value)
 *           &gt;
 *   Environment variable (KEY_WITH_UNDERSCORES)
 *           &gt;
 *   Project YAML (sdk-config.yaml, via YamlConfigReader)
 *           &gt;
 *   SDK default (caller-supplied fallback)
 * </pre>
 *
 * <p>{@link com.test.automation.sdk.mobile.config.MobileConfigReader} is a
 * typed Mobile view over this engine: it still owns the temporary
 * standalone-{@code mobile-config.yaml} compatibility path (see its javadoc),
 * but for every key not present in that optional file it now delegates to
 * {@link #resolve(String, String)} instead of reading
 * {@link YamlConfigReader} directly, so the same precedence rules apply to
 * Web and Mobile configuration alike.</p>
 *
 * <p>{@link #getCommonConfig()} / {@link #getWebConfig()} return small typed
 * views over the handful of keys those callers already relied on, without
 * introducing a broader configuration object model than currently needed.</p>
 */
public final class ConfigurationManager {

    private ConfigurationManager() {
    }

    /**
     * Resolves {@code key} using the full precedence chain (system property
     * &gt; environment variable &gt; project YAML &gt; {@code defaultValue}).
     */
    public static String resolve(String key, String defaultValue) {
        String override = resolveOverride(key);
        if (override != null) {
            return override;
        }
        return YamlConfigReader.get(key, defaultValue);
    }

    /** Boolean convenience wrapper around {@link #resolve(String, String)}. */
    public static boolean resolveBoolean(String key, boolean defaultValue) {
        String value = resolve(key, null);
        return (value != null) ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    /** Integer convenience wrapper around {@link #resolve(String, String)}. */
    public static int resolveInt(String key, int defaultValue) {
        String value = resolve(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns the highest-precedence override for {@code key} -- a JVM
     * system property or environment variable -- or {@code null} when
     * neither is set, so callers with their own lower-precedence data
     * source (e.g. a standalone {@code mobile-config.yaml}) can check this
     * first without duplicating the system-property/env lookup logic.
     */
    public static String resolveOverride(String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isEmpty()) {
            return sys;
        }
        String env = System.getenv(toEnvName(key));
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return null;
    }

    private static String toEnvName(String key) {
        return key.toUpperCase().replace('.', '_').replace('-', '_');
    }

    /** Typed view over platform-neutral (common) configuration. */
    public static CommonConfig getCommonConfig() {
        return new CommonConfig();
    }

    /** Typed view over the handful of Web-specific keys used today. */
    public static WebConfig getWebConfig() {
        return new WebConfig();
    }

    /**
     * Typed view over the automatic Allure HTML report generation feature
     * (OBS-9). Resolved through the same system property &gt; environment
     * variable &gt; project YAML &gt; default precedence chain as every other
     * SDK setting -- see {@link com.test.automation.sdk.reporting.AllureReportGenerator}
     * for how these values drive the post-execution lifecycle.
     */
    public static AllureReportConfig getAllureReportConfig() {
        return new AllureReportConfig();
    }

    /**
     * Typed view over the cross-run analytics event store
     * ({@link com.test.automation.sdk.reporting.AnalyticsExecutionReporter}):
     * every {@code ExecutionEvent} is appended as one JSON line to a per-run
     * file, enabling trend analysis (pass-rate history, flaky-test detection,
     * healed-locator frequency) across many executions -- something no
     * single-run Allure/Extent report can answer on its own. See
     * {@link com.test.automation.sdk.reporting.AnalyticsTrendReport}.
     */
    public static AnalyticsConfig getAnalyticsConfig() {
        return new AnalyticsConfig();
    }

    /**
     * Typed view over the zero-config visual regression feature
     * ({@link com.test.automation.sdk.visual.VisualRegressionChecker}):
     * screenshot-baseline pixel-diff comparisons with configurable mismatch
     * tolerance, without requiring any external visual-testing service.
     */
    public static VisualRegressionConfig getVisualRegressionConfig() {
        return new VisualRegressionConfig();
    }

    /**
     * Typed view over the flaky-test quarantine feature
     * ({@link com.test.automation.sdk.flaky.FlakyTestQuarantineListener}):
     * uses the cross-run analytics history to distinguish a genuinely
     * intermittent ("known flaky") test from a first-time regression or a
     * consistently-broken test, and -- only when explicitly enabled --
     * reclassifies a known-flaky test's final failure as a skip so it does
     * not block the build, while still surfacing it clearly in reports.
     */
    public static FlakyQuarantineConfig getFlakyQuarantineConfig() {
        return new FlakyQuarantineConfig();
    }

    /**
     * Typed, read-only view over the test-impact-analysis configuration used by
     * {@code com.test.automation.sdk.impact.TestImpactCli}. This tool maps changed
     * source files (from {@code git diff}) to affected test classes via a
     * heuristic source-reference graph, then emits a filtered TestNG suite so CI
     * can run only the impacted tests.
     */
    public static TestImpactConfig getTestImpactConfig() {
        return new TestImpactConfig();
    }

    /**
     * Typed, read-only view over the API-testing module configuration used by
     * {@code com.test.automation.sdk.api.ApiTestBase}: per-environment base URLs,
     * a single optional auth header sourced from an environment variable (never
     * a config file/YAML value, so secrets are never committed), timeouts, and
     * request/response logging toggles.
     */
    public static ApiConfig getApiConfig() {
        return new ApiConfig();
    }

    /** Typed view over one remote execution provider's configuration. */
    public static RemoteProviderConfig getRemoteProviderConfig(String providerId) {
        return new RemoteProviderConfig(providerId);
    }

    /**
     * Typed, read-only view over the automated RCA-to-fix bundle feature
     * ({@link com.test.automation.sdk.reporting.RcaBundleWriter}): on every
     * test failure, consolidates the screenshot path, DOM dump path, a tail
     * of the shared SDK log, and the exception/step context into a single
     * JSON file -- so a human or Copilot can propose a fix from one
     * artifact instead of hunting down three separate files.
     */
    public static RcaBundleConfig getRcaBundleConfig() {
        return new RcaBundleConfig();
    }

    /**
     * Typed, read-only view over the SDK v1.5.1 failure-evidence pipeline
     * ({@code evidence.*}): screenshot/browser-console/network capture and
     * report-attachment toggles. Screenshot and DOM capture remain the
     * pre-v1.5.1 mandatory RCA evidence and are always attempted on failure
     * regardless of these flags; these settings additionally control whether
     * they (and the new console/network evidence) are attached to Allure/Extent.
     */
    public static EvidenceConfig getEvidenceConfig() {
        return new EvidenceConfig();
    }

    /**
     * Typed, read-only view over {@link com.test.automation.sdk.listener.WebEventListener}'s
     * non-terminal-exception classification (SDK v1.5.1): exception class names that
     * must not independently create an {@code ExecutionReporting.actionFailed(...)}
     * record when observed by {@code onError()} -- see that class's javadoc for the
     * distinction between "non-terminal at listener level" and "final test failure".
     */
    public static WebEventListenerConfig getWebEventListenerConfig() {
        return new WebEventListenerConfig();
    }

    /** Typed, read-only configuration for {@code providers.<id>}. */
    public static final class RemoteProviderConfig {
        private final String providerId;

        private RemoteProviderConfig(String providerId) {
            if (providerId == null || !providerId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("providerId must be a canonical lower-case identifier");
            }
            this.providerId = providerId;
        }

        public URI hubUri() {
            String key = "providers." + providerId + ".hubUrl";
            String rawValue = resolve(key, "");
            if (rawValue == null || rawValue.trim().isEmpty()) {
                throw new IllegalStateException("Missing required remote provider setting: " + key);
            }
            final URI uri;
            try {
                uri = new URI(rawValue.trim());
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Invalid remote provider URL in " + key, e);
            }
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException(key + " must be an absolute HTTP(S) URL with a host");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalStateException(key + " must not contain credentials; use environment variables");
            }
            if (uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException(key + " must not contain a query or fragment");
            }
            if ("http".equalsIgnoreCase(scheme)
                    && !resolveBoolean("providers." + providerId + ".allowInsecureHttp", false)) {
                throw new IllegalStateException(key
                        + " uses HTTP; set providers." + providerId + ".allowInsecureHttp=true only for a trusted grid");
            }
            return uri;
        }
    }

    /** Typed, read-only view over platform-neutral (common) configuration. */
    public static final class CommonConfig {
        private CommonConfig() {
        }

        public String loggingLevel() {
            return resolve("logging.level", "INFO");
        }

        public String screenshotsDir() {
            return resolve("reporting.screenshotsDir", "test-output/screenshots");
        }

        public String logsDir() {
            return resolve("reporting.logsDir", "test-output/logs");
        }
    }

    /** Typed, read-only view over Web-specific configuration. */
    public static final class WebConfig {
        private WebConfig() {
        }

        public String defaultBrowser() {
            return resolve("browser.default", "chrome");
        }

        public boolean headless() {
            return resolveBoolean("browser.headless", false);
        }

        public int pageLoadTimeoutSeconds() {
            return resolveInt("browser.pageLoadTimeoutSeconds", 30);
        }
    }

    /**
     * Typed, read-only view over the OBS-9 automatic Allure report
     * generation configuration.
     */
    public static final class AllureReportConfig {
        private AllureReportConfig() {
        }

        /** Master on/off switch for the whole feature. */
        public boolean enabled() {
            return resolveBoolean("reporting.allure.enabled", true);
        }

        /** Whether to run {@code allure generate} after execution completes. */
        public boolean generateAfterExecution() {
            return resolveBoolean("reporting.allure.generateAfterExecution", true);
        }

        /**
         * Whether to open the generated report after a successful generation.
         * Deliberately defaults to {@code false} -- opening a browser must
         * never happen automatically on a CI/service/headless agent.
         */
        public boolean openAfterGeneration() {
            return resolveBoolean("reporting.allure.openAfterGeneration", false);
        }

        /** Directory Allure result files are written to during the run. */
        public String resultsDirectory() {
            return resolve("reporting.allure.resultsDirectory", "allure-results");
        }

        /** Destination directory for the generated static HTML report. */
        public String reportDirectory() {
            return resolve("reporting.allure.reportDirectory", "allure-report");
        }

        /** Maximum time to wait for {@code allure generate} to finish. */
        public int generationTimeoutSeconds() {
            return resolveInt("reporting.allure.generationTimeoutSeconds", 120);
        }
    }

    /**
     * Typed, read-only view over the cross-run analytics event store
     * configuration.
     */
    public static final class AnalyticsConfig {
        private AnalyticsConfig() {
        }

        /** Master on/off switch for the whole feature. Defaults to enabled -- write failures never fail a test. */
        public boolean enabled() {
            return resolveBoolean("reporting.analytics.enabled", true);
        }

        /** Directory one JSON-lines file per run is written to. */
        public String directory() {
            return resolve("reporting.analytics.directory", "test-output/analytics");
        }
    }

    /**
     * Typed, read-only view over the automated RCA-to-fix bundle
     * configuration.
     */
    public static final class RcaBundleConfig {
        private RcaBundleConfig() {
        }

        /** Master on/off switch for the whole feature. Defaults to enabled -- write failures never fail a test. */
        public boolean enabled() {
            return resolveBoolean("reporting.rcaBundle.enabled", true);
        }

        /** Directory one JSON file per failure is written to. */
        public String directory() {
            return resolve("reporting.rcaBundle.directory", "test-output/rca-bundles");
        }

        /** Number of trailing lines pulled from the shared SDK log file into each bundle. */
        public int logTailLines() {
            return resolveInt("reporting.rcaBundle.logTailLines", 80);
        }

        /** Number of leading stack-trace frames included per exception/cause in each bundle. */
        public int stackTraceFrames() {
            return resolveInt("reporting.rcaBundle.stackTraceFrames", 15);
        }
    }

    /**
     * Typed, read-only view over the zero-config visual regression
     * configuration.
     */
    public static final class VisualRegressionConfig {
        private VisualRegressionConfig() {
        }

        /** Master on/off switch for the whole feature. */
        public boolean enabled() {
            return resolveBoolean("visual.enabled", true);
        }

        /**
         * Directory accepted baseline screenshots are stored in. Defaults to
         * a location under the consumer project's test resources so
         * baselines can be committed to version control and reviewed like
         * any other test asset.
         */
        public String baselineDirectory() {
            return resolve("visual.baselineDirectory", "src/test/resources/visual-baselines");
        }

        /** Directory actual/diff screenshots from the current run are written to. */
        public String outputDirectory() {
            return resolve("visual.outputDirectory", "test-output/visual");
        }

        /** Maximum acceptable mismatch percentage (0-100) before a check is considered failed. */
        public double mismatchThresholdPercent() {
            String value = resolve("visual.mismatchThresholdPercent", null);
            if (value == null) {
                return 0.1;
            }
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                return 0.1;
            }
        }

        /** Maximum per-channel (0-255) color delta still considered "the same pixel" -- absorbs anti-aliasing noise. */
        public int pixelColorTolerance() {
            return resolveInt("visual.pixelColorTolerance", 12);
        }

        /**
         * When {@code true}, every check overwrites the stored baseline with
         * the current screenshot instead of comparing against it -- used for
         * deliberate re-baselining runs (e.g. {@code -Dvisual.updateBaselines=true})
         * after an intentional UI change.
         */
        public boolean updateBaselines() {
            return resolveBoolean("visual.updateBaselines", false);
        }

        /**
         * When {@code true} (default), a mismatch throws an
         * {@link AssertionError} from {@code TestBase.assertVisualMatch}.
         * When {@code false}, mismatches are only reported/logged, useful
         * while first introducing visual checks into an existing suite.
         */
        public boolean failOnMismatch() {
            return resolveBoolean("visual.failOnMismatch", true);
        }
    }

    /**
     * Typed, read-only view over the flaky-test quarantine configuration.
     */
    public static final class FlakyQuarantineConfig {
        private FlakyQuarantineConfig() {
        }

        /**
         * Master on/off switch. Defaults to {@code false} -- automatically
         * converting a failure into a skip is an opinionated, potentially
         * risky behavior change that a project must deliberately opt into.
         */
        public boolean enabled() {
            return resolveBoolean("flaky.quarantine.enabled", false);
        }

        /** Minimum number of historical runs required before a test can be quarantined. */
        public int minRunsForQuarantine() {
            return resolveInt("flaky.minRunsForQuarantine", 5);
        }

        /**
         * Maximum historical failure rate (0-100) still considered "flaky"
         * rather than "broken". A test failing more often than this is
         * treated as consistently broken and keeps failing the build normally.
         */
        public double maxFailureRatePercent() {
            String value = resolve("flaky.maxFailureRatePercent", null);
            if (value == null) {
                return 80.0;
            }
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                return 80.0;
            }
        }

        /** Directory the analytics event store writes {@code *.jsonl} run history to (shared with {@link AnalyticsConfig}). */
        public String analyticsDirectory() {
            return resolve("reporting.analytics.directory", "test-output/analytics");
        }
    }

    /** Typed, read-only view over the test-impact-analysis configuration. */
    public static final class TestImpactConfig {
        private TestImpactConfig() {
        }

        /** Main source root scanned to build the class-reference graph. */
        public String mainSourceDir() {
            return resolve("impact.mainSourceDir", "src/main/java");
        }

        /** Test source root scanned to build the class-reference graph and locate test classes. */
        public String testSourceDir() {
            return resolve("impact.testSourceDir", "src/test/java");
        }

        /**
         * Regex a simple (unqualified) class name must match to be considered a test class
         * eligible for inclusion in the generated impact suite. Defaults to this SDK's own
         * {@code Test_*} naming convention.
         */
        public String testClassNamePattern() {
            return resolve("impact.testClassNamePattern", "Test_.*");
        }

        /** Where the generated impact-only TestNG suite XML is written. */
        public String outputSuiteFile() {
            return resolve("impact.outputSuiteFile", "test-output/impact/impact_suite.xml");
        }

        /** Git ref (or revision range base) diffed against the working tree to find changed files. */
        public String baseRef() {
            return resolve("impact.baseRef", "HEAD~1");
        }
    }

    /**
     * Typed, read-only view over the API-testing module configuration.
     * Base URLs are resolved per-environment using the same
     * "one otherwise-identical key per environment" convention already used
     * by {@code TestBase.setBaseUrl(environment)} and this org's legacy API
     * projects (e.g. {@code stg_base_url}, {@code prd_base_url}): the key
     * looked up is {@code api.baseUrl.<environment>}, falling back to the
     * environment-neutral {@code api.baseUrl} when no per-environment
     * override is configured.
     */
    public static final class ApiConfig {
        private ApiConfig() {
        }

        /**
         * Resolves the base URL for the given environment: tries
         * {@code api.baseUrl.<environment>} first, then falls back to the
         * environment-neutral {@code api.baseUrl}.
         *
         * @param environment environment name (e.g. {@code "stg"}, {@code "prd"}); may be null/empty
         * @return the resolved base URL, or {@code null} if neither key is configured
         */
        public String baseUrl(String environment) {
            if (environment != null && !environment.trim().isEmpty()) {
                String perEnvironment = resolve("api.baseUrl." + environment.trim(), null);
                if (perEnvironment != null) {
                    return perEnvironment;
                }
            }
            return resolve("api.baseUrl", null);
        }

        /**
         * Name of the single HTTP header (e.g. {@code Ocp-Apim-Subscription-Key},
         * {@code Authorization}) automatically attached to every request built via
         * {@code ApiTestBase.given()}, when both this and {@link #authTokenEnvVar()}
         * are configured. Empty/unset disables automatic header injection entirely.
         */
        public String authHeaderName() {
            return resolve("api.authHeaderName", "");
        }

        /**
         * Name of the environment variable whose value becomes the auth header's
         * value. Deliberately an environment-variable NAME, never the secret value
         * itself, so no credential is ever stored in {@code sdk-config.yaml}.
         */
        public String authTokenEnvVar() {
            return resolve("api.authTokenEnvVar", "");
        }

        /** Connection timeout (milliseconds) applied to every request. */
        public int connectionTimeoutMillis() {
            return resolveInt("api.connectionTimeoutMillis", 10000);
        }

        /** Socket/read timeout (milliseconds) applied to every request. */
        public int readTimeoutMillis() {
            return resolveInt("api.readTimeoutMillis", 30000);
        }

        /** Whether request/response bodies are logged and captured as evidence on failure. */
        public boolean logRequestsAndResponses() {
            return resolveBoolean("api.logRequestsAndResponses", true);
        }

        /** Directory API request/response payload evidence files are written to. */
        public String outputDirectory() {
            return resolve("api.outputDirectory", "test-output/api");
        }

        /**
         * Whether TLS certificate/hostname validation is relaxed. Defaults to
         * {@code false} -- must be deliberately opted into for lower
         * (non-production) environments only, never left on by default.
         */
        public boolean relaxedHttpsValidation() {
            return resolveBoolean("api.relaxedHttpsValidation", false);
        }
    }

    /**
     * Typed, read-only view over the SDK v1.5.1 failure-evidence pipeline.
     * All keys follow the {@code evidence.<kind>.<setting>} shape shown in the
     * v1.5.1 spec and are resolved through the standard system property &gt;
     * environment variable &gt; project YAML &gt; default precedence chain.
     */
    public static final class EvidenceConfig {
        private EvidenceConfig() {
        }

        /** Master screenshot-evidence switch. Screenshots remain part of the core RCA trio. */
        public boolean screenshotEnabled() {
            return resolveBoolean("evidence.screenshot.enabled", true);
        }

        /** Whether the failure screenshot is attached directly to Allure/Extent (in addition to the filesystem artifact). */
        public boolean screenshotAttachToReports() {
            return resolveBoolean("evidence.screenshot.attachToReports", true);
        }

        /**
         * Master browser-console-capture switch. Enabled by default: retrieval
         * has negligible overhead on supported browsers and is skipped (fail-safe,
         * not fatal) on browsers/log types that do not support it -- see
         * {@code com.test.automation.sdk.evidence.BrowserConsoleCapture}.
         */
        public boolean browserConsoleEnabled() {
            return resolveBoolean("evidence.browserConsole.enabled", true);
        }

        /** Whether console capture only runs on test failure (the only supported mode in v1.5.1). */
        public boolean browserConsoleCaptureOnFailure() {
            return resolveBoolean("evidence.browserConsole.captureOnFailure", true);
        }

        /** Whether the captured console log is attached to Allure/Extent. */
        public boolean browserConsoleAttachToReports() {
            return resolveBoolean("evidence.browserConsole.attachToReports", true);
        }

        /**
         * Master network-trace switch. Disabled by default -- performance
         * overhead, artifact size, and browser-specific implementation
         * differences (Chrome DevTools Protocol only; Firefox unsupported --
         * see {@code com.test.automation.sdk.evidence.NetworkTraceRecorder}).
         */
        public boolean networkEnabled() {
            return resolveBoolean("evidence.network.enabled", false);
        }

        /** Whether the buffered trace is only persisted to disk on test failure (recommended, avoids unnecessary I/O for passing tests). */
        public boolean networkCaptureOnFailure() {
            return resolveBoolean("evidence.network.captureOnFailure", true);
        }

        /** Whether the persisted network trace is attached to Allure/Extent. */
        public boolean networkAttachToReports() {
            return resolveBoolean("evidence.network.attachToReports", true);
        }

        /** Whether sensitive headers/cookies/tokens/query-params are masked before the trace is persisted/attached. */
        public boolean networkRedactSensitiveData() {
            return resolveBoolean("evidence.network.redactSensitiveData", true);
        }

        /** Maximum number of buffered network request/response pairs retained per test session before oldest entries are dropped. */
        public int networkMaxEntries() {
            return resolveInt("evidence.network.maxEntries", 500);
        }
    }

    /**
     * Typed, read-only view over {@link com.test.automation.sdk.listener.WebEventListener}'s
     * non-terminal-exception classification.
     */
    public static final class WebEventListenerConfig {
        private WebEventListenerConfig() {
        }

        /**
         * Comma-separated list of fully-qualified exception class names that must
         * not independently create a listener-level {@code ExecutionReporting.actionFailed(...)}
         * record. Defaults to {@code org.openqa.selenium.NoSuchElementException} only --
         * additional types (e.g. {@code StaleElementReferenceException}) require
         * explicit configuration since they can represent genuine automation defects.
         */
        public String nonTerminalExceptions() {
            return resolve("webdriver.eventListener.nonTerminalExceptions",
                    "org.openqa.selenium.NoSuchElementException");
        }
    }
}
