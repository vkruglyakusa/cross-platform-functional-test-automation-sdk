package com.test.automation.sdk.mobile.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.test.automation.sdk.mobile.testbase.MobileSdkConfig;

/**
 * Reads mobile-config.yaml (Android/iOS app paths, local Appium URL, device
 * defaults, etc.) from the configuration directory resolved by {@link MobileSdkConfig}.
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
 */
public final class MobileConfigReader {

    private static final Logger log = LogManager.getLogger(MobileConfigReader.class.getName());
    private static MobileConfigReader instance;
    private final Map<String, String> flatMap = new HashMap<>();

    private MobileConfigReader() {
        File yamlFile = new File(MobileSdkConfig.MOBILE_CONFIG_YAML);
        if (!yamlFile.exists()) {
            log.warn("[MobileConfigReader] mobile-config.yaml not found at: {} -- using defaults",
                    MobileSdkConfig.MOBILE_CONFIG_YAML);
            loadDefaults();
            return;
        }
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
        String value = getInstance().flatMap.get(dottedKey);
        return (value == null || value.isEmpty()) ? defaultValue : value;
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
