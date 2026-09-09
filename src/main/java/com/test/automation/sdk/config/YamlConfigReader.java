package com.test.automation.sdk.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads {@code sdk-config.yaml} from the configuration directory.
 *
 * Uses a minimal YAML parser to avoid requiring SnakeYAML on the classpath
 * when not needed. Supports simple key: value pairs and nested sections.
 *
 * Access via static singleton: {@code YamlConfigReader.get("logging.level")}
 *
 * <p><b>Structure Cleanup Phase 2</b>
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md) -- this is
 * now the single, real, platform-neutral implementation. It previously lived
 * in {@link com.test.automation.sdk.utility.YamlConfigReader}, which is now a
 * deprecated compatibility facade that delegates here; all new code should
 * reference this class directly.
 *
 * <p>Carries built-in defaults for {@code appium.localUrl},
 * {@code android.automationName}, and {@code ios.automationName}, so a
 * project's {@code android:}/{@code ios:} sections can be configured directly
 * here instead of in a separate {@code mobile-config.yaml} (see
 * {@link com.test.automation.sdk.mobile.config.MobileConfigReader}).
 */
public final class YamlConfigReader {

    private static final Logger log = LogManager.getLogger(YamlConfigReader.class.getName());
    private static YamlConfigReader instance;
    private final Map<String, String> flatMap = new HashMap<String, String>();

    private YamlConfigReader() {
        // Resolve path dynamically so -Dsdk.config.dir overrides work at runtime
        String dir = System.getProperty("sdk.config.dir");
        if (dir == null || dir.isEmpty()) {
            dir = System.getenv("SDK_CONFIG_DIR");
        }
        if (dir == null || dir.isEmpty()) {
            dir = "configuration";
        }
        dir = dir.replaceAll("[/\\\\]+$", "");
        String yamlPath = dir + "/sdk-config.yaml";

        File yamlFile = new File(yamlPath);
        if (!yamlFile.exists()) {
            log.warn("[YamlConfigReader] sdk-config.yaml not found at: {} -- using defaults", yamlPath);
            loadDefaults();
            bridgeAccessibilityConfig();
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(yamlFile);
            try {
                parse(fis);
                log.info("[YamlConfigReader] Loaded sdk-config.yaml from: {}", yamlPath);
            } finally {
                fis.close();
            }
        } catch (IOException e) {
            log.error("[YamlConfigReader] Failed to read sdk-config.yaml: {}", e.getMessage());
            loadDefaults();
        }
        // Bridge accessibility.* keys into system properties so A11yConfig.get() resolves them
        // without requiring a separate accessibility.properties file on the classpath.
        bridgeAccessibilityConfig();
    }

    private void parse(InputStream is) throws IOException {
        byte[] bytes = readAllBytes(is);
        String content = new String(bytes, "UTF-8");
        String[] lines = content.split("\\r?\\n");
        String currentSection = "";
        String currentSubSection = "";
        for (String rawLine : lines) {
            // Strip comments
            String line = rawLine;
            int commentIdx = line.indexOf('#');
            if (commentIdx >= 0) {
                line = line.substring(0, commentIdx);
            }
            if (line.trim().isEmpty()) continue;

            // Count leading spaces to determine nesting level
            int indent = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') indent++;
                else break;
            }
            String trimmed = line.trim();
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx < 0) continue;

            String key = trimmed.substring(0, colonIdx).trim();
            String value = trimmed.substring(colonIdx + 1).trim();
            // Strip surrounding quotes (single or double)
            if (value.length() >= 2
                    && ((value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
                    ||  (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''))) {
                value = value.substring(1, value.length() - 1);
            }

            if (indent == 0) {
                currentSection = key;
                currentSubSection = "";
            } else if (indent == 2) {
                currentSubSection = key;
                if (!value.isEmpty()) {
                    flatMap.put(currentSection + "." + key, value);
                }
            } else if (indent == 4) {
                if (!value.isEmpty()) {
                    flatMap.put(currentSection + "." + currentSubSection + "." + key, value);
                }
            }
        }
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = is.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private void loadDefaults() {
        flatMap.put("browser.default", "chrome");
        flatMap.put("browser.headless", "false");
        flatMap.put("browser.pageLoadTimeoutSeconds", "30");
        flatMap.put("browser.implicitWaitSeconds", "0");
        flatMap.put("proxy.enabled", "false");
        flatMap.put("proxy.host", "");
        flatMap.put("proxy.port", "8080");
        flatMap.put("proxy.bypassList", "localhost;<-loopback>");
        flatMap.put("logging.level", "INFO");
        flatMap.put("logging.sdkLogFile",      "test-output/logs/sdk.log");
        flatMap.put("logging.seleniumLogFile",  "test-output/logs/selenium.log");
        flatMap.put("logging.browserLogFile",   "test-output/logs/browser.log");
        flatMap.put("logging.enableSeleniumLogs",         "true");
        flatMap.put("logging.enableBrowserConsoleLogs",   "true");
        flatMap.put("screenshots.captureOnFailure", "true");
        // Unified reporting output directories (single source of truth)
        flatMap.put("reporting.screenshotsDir",   "test-output/screenshots");
        flatMap.put("reporting.domDumpsDir",       "test-output/dom-dumps");
        flatMap.put("reporting.logsDir",           "test-output/logs");
        flatMap.put("reporting.crawlerDir",        "test-output/crawler");
        flatMap.put("reporting.accessibilityDir",  "test-output/accessibility");
        flatMap.put("reporting.gapOutputDir",      "docs/test-case-gaps");
        // Backward-compat aliases so existing code reading old keys still works
        flatMap.put("screenshots.outputDir",       "test-output/screenshots");
        flatMap.put("screenshots.domDumpDir",       "test-output/dom-dumps");
        flatMap.put("crawler.pageObject.package",   "com.mycompany.automation.uiActions");
        flatMap.put("crawler.pageObject.outputDir", "src/main/java/com/mycompany/automation/uiActions/");
        flatMap.put("crawler.pageObject.reportDir", "test-output/crawler/");
        flatMap.put("api.mailinator.apiKey", "");
        flatMap.put("api.mailinator.domain", "mailinator.com");
        flatMap.put("api.mailinator.privateDomain", "true");
        flatMap.put("api.mailinator.inboxPollIntervalSeconds", "3");
        flatMap.put("api.mailinator.inboxPollTimeoutSeconds", "60");
        flatMap.put("api.mailinator.inboxInitialWaitSeconds", "5");
        // Accessibility defaults
        flatMap.put("accessibility.checking.enabled",          "false");
        flatMap.put("accessibility.fail.on.violation",         "false");
        flatMap.put("accessibility.wcag.tags",                 "wcag2a,wcag2aa");
        flatMap.put("accessibility.output.dir",                "test-output/accessibility");
        flatMap.put("accessibility.debug",                     "false");
        flatMap.put("accessibility.session.noise.threshold",   "MINOR");
        flatMap.put("accessibility.session.max.scans.per.url", "1");
        flatMap.put("accessibility.session.dedup.cooldown.ms", "0");
        flatMap.put("accessibility.session.dedup.dom.fingerprint", "true");
        flatMap.put("accessibility.session.allowed.rules",     "");
        flatMap.put("accessibility.session.allowed.urls",      "");
        flatMap.put("accessibility.session.spa.poll.interval.ms", "0");
        flatMap.put("accessibility.scan.wait.enabled",         "true");
        flatMap.put("accessibility.scan.wait.timeout.ms",      "5000");
        flatMap.put("accessibility.scan.iframe.max.depth",     "3");
        flatMap.put("accessibility.engine.interaction.enabled","true");
        flatMap.put("accessibility.engine.wcag22.enabled",     "true");
        flatMap.put("accessibility.engine.structural.enabled", "true");
        flatMap.put("accessibility.engine.motion.enabled",     "true");
        flatMap.put("accessibility.scan.on.dialog",            "false");
        flatMap.put("accessibility.scan.dialog.poll.interval.ms", "1000");
        // Mobile defaults (android.*/ios.* sections merged into this file)
        flatMap.put("appium.localUrl", "http://127.0.0.1:4723/");
        flatMap.put("android.automationName", "UiAutomator2");
        flatMap.put("ios.automationName", "XCUITest");
    }

    /**
     * Bridges all {@code accessibility.*} yaml values into JVM system properties
     * so that {@code A11yConfig.get(key)} (which reads {@code System.getProperty})
     * can resolve them without requiring a separate {@code accessibility.properties}
     * file.  Existing system properties set via {@code -D} flags are never
     * overwritten, preserving the standard override chain:
     * <pre>
     *   -D flag > env var > sdk-config.yaml > built-in defaults
     * </pre>
     * Called automatically during initialisation.
     */
    private void bridgeAccessibilityConfig() {
        for (Map.Entry<String, String> entry : flatMap.entrySet()) {
            String key = entry.getKey();
            // Bridge all accessibility.* keys AND the unified reporting.accessibilityDir key
            // so that A11yConfig.get() (which reads System.getProperty) can resolve them.
            boolean isA11yKey = key.startsWith("accessibility.");
            boolean isReportingA11yKey = key.equals("reporting.accessibilityDir");
            if (isA11yKey || isReportingA11yKey) {
                if (System.getProperty(key) == null) {
                    String value = entry.getValue();
                    if (value != null && !value.isEmpty()) {
                        System.setProperty(key, value);
                    }
                }
            }
        }
        log.debug("[YamlConfigReader] Accessibility config bridged to system properties.");
    }

    private static synchronized YamlConfigReader getInstance() {
        if (instance == null) {
            instance = new YamlConfigReader();
        }
        return instance;
    }

    /**
     * Get a config value by dot-separated key path.
     * Example: YamlConfigReader.get("logging.level")
     * @return value or null if not found
     */
    public static String get(String key) {
        return getInstance().flatMap.get(key);
    }

    /**
     * Get a config value with a default fallback.
     */
    public static String get(String key, String defaultValue) {
        String val = getInstance().flatMap.get(key);
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    /**
     * Get a boolean config value.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val.trim());
    }

    /**
     * Get an integer config value.
     */
    public static int getInt(String key, int defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns an unmodifiable view of all loaded config entries.
     */
    public static Map<String, String> getAll() {
        return Collections.unmodifiableMap(getInstance().flatMap);
    }
}
