package com.test.automation.sdk.config;

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
}
