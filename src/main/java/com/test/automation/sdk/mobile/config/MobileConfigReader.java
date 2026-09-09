package com.test.automation.sdk.mobile.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.test.automation.sdk.config.SdkConfig;

/**
 * Reads mobile-config.yaml (Android/iOS app paths, local Appium URL, device
 * defaults, etc.) from the configuration directory resolved by {@link SdkConfig}.
 *
 * Deliberately uses the same minimal flat-key YAML parsing strategy as
 * com.test.automation.sdk.utility.YamlConfigReader in the desktop SDK, to avoid
 * pulling in a SnakeYAML dependency for a handful of simple key: value settings.
 * Supports one level of nesting via dotted keys, e.g.:
 *
 *   android:
 *     appPath: apps/app-debug.apk
 *     deviceName: Pixel_6_API_34
 *
 * is read back via get("android.appPath") / get("android.deviceName").
 *
 * As of Phase 3 of the unified web+mobile SDK architecture
 * (docs/proposals/unified-sdk-architect-review.md), a standalone
 * mobile-config.yaml is no longer required: when the file is absent, {@link #get}
 * falls back to reading the same dotted keys (e.g. {@code android.appPath})
 * directly from the unified {@code sdk-config.yaml} via
 * {@link com.test.automation.sdk.config.YamlConfigReader}. Projects that
 * still ship a standalone mobile-config.yaml keep working unchanged for one
 * release.
 */
public final class MobileConfigReader {

    private static final Logger log = LogManager.getLogger(MobileConfigReader.class.getName());
    private static MobileConfigReader instance;
    private final Map<String, String> flatMap = new HashMap<>();
    private final boolean usingStandaloneFile;

    private MobileConfigReader() {
        File yamlFile = new File(SdkConfig.MOBILE_CONFIG_YAML);
        if (!yamlFile.exists()) {
            usingStandaloneFile = false;
            log.info("[MobileConfigReader] No standalone mobile-config.yaml found at: {} -- "
                            + "reading android.*/ios.* sections from the unified sdk-config.yaml instead.",
                    SdkConfig.MOBILE_CONFIG_YAML);
            return;
        }
        usingStandaloneFile = true;
        try (InputStream in = new FileInputStream(yamlFile)) {
            parse(in);
        } catch (IOException e) {
            log.error("[MobileConfigReader] Failed to read mobile-config.yaml, using defaults", e);
            loadDefaults();
        }
    }

    private static synchronized MobileConfigReader getInstance() {
        if (instance == null) {
            instance = new MobileConfigReader();
        }
        return instance;
    }

    /** Returns the configured value for a dotted key, or {@code defaultValue} if absent. */
    public static String get(String dottedKey, String defaultValue) {
        MobileConfigReader inst = getInstance();
        if (inst.usingStandaloneFile) {
            String value = inst.flatMap.get(dottedKey);
            return (value == null || value.isEmpty()) ? defaultValue : value;
        }
        // No standalone file: the unified sdk-config.yaml (extended in Phase 3
        // with the same android.*/ios.*/appium.* default keys) is the source of truth.
        return com.test.automation.sdk.config.YamlConfigReader.get(dottedKey, defaultValue);
    }

    private void loadDefaults() {
        flatMap.put("appium.localUrl", "http://127.0.0.1:4723/");
        flatMap.put("android.automationName", "UiAutomator2");
        flatMap.put("ios.automationName", "XCUITest");
    }

    /**
     * Minimal parser: supports top-level "key: value" lines and one level of
     * nesting via 2-space (or tab) indentation, e.g. "section:" followed by
     * indented "childKey: value" lines, flattened to "section.childKey".
     * Ignores blank lines and lines starting with '#'.
     */
    private void parse(InputStream in) throws IOException {
        java.io.BufferedReader reader =
                new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        String currentSection = null;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            boolean indented = line.startsWith(" ") || line.startsWith("\t");
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }
            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();
            value = stripQuotes(value);

            if (value.isEmpty()) {
                // Section header, e.g. "android:"
                currentSection = indented ? currentSection : key;
                continue;
            }
            String fullKey = (indented && currentSection != null) ? currentSection + "." + key : key;
            flatMap.put(fullKey, value);
        }
        if (flatMap.isEmpty()) {
            loadDefaults();
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
