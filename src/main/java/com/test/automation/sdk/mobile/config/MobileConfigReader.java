package com.test.automation.sdk.mobile.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.test.automation.sdk.config.ConfigurationManager;
import com.test.automation.sdk.config.SdkConfig;

/**
 * Typed Mobile view over the SDK's single configuration-resolution engine,
 * {@link ConfigurationManager} (Unified SDK Review Priority 2, section 8 of
 * docs/proposals/Unified_SDK_Implementation_Review_Findings_2026-09-09.md).
 *
 * <p>This class owns exactly one piece of Mobile-specific behavior: the
 * temporary compatibility path for a standalone {@code mobile-config.yaml}
 * (Android/iOS app paths, local Appium URL, device defaults, etc.), read with
 * the same minimal flat-key YAML parsing strategy used elsewhere in the SDK
 * to avoid pulling in a SnakeYAML dependency for a handful of simple
 * key: value settings. Supports one level of nesting via dotted keys, e.g.:
 *
 *   android:
 *     appPath: apps/app-debug.apk
 *     deviceName: Pixel_6_API_34
 *
 * is read back via get("android.appPath") / get("android.deviceName").
 *
 * <p>For every key not present in that optional standalone file -- which, as
 * of Phase 3 of the unified web+mobile SDK architecture, includes the normal
 * case where no standalone file exists at all -- resolution delegates to
 * {@link ConfigurationManager#resolve(String, String)}, so Mobile
 * configuration is subject to the exact same
 * system-property &gt; environment-variable &gt; project-YAML &gt; default
 * precedence chain as Web/common configuration, rather than maintaining its
 * own independent resolver. Compatibility with a standalone
 * {@code mobile-config.yaml} is retained only as a temporary migration
 * mechanism per the review's Priority 2 guidance and may be removed once
 * consumer projects have migrated their {@code android.}/{@code ios.}
 * sections into {@code sdk-config.yaml}.
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

    /**
     * Returns the configured value for a dotted key, or {@code defaultValue}
     * if absent anywhere in the chain.
     *
     * <p>Resolution order: standalone {@code mobile-config.yaml} (if present,
     * temporary compatibility path) &gt; {@link ConfigurationManager}'s
     * system-property &gt; environment-variable &gt; project-YAML &gt; default
     * chain.</p>
     */
    public static String get(String dottedKey, String defaultValue) {
        MobileConfigReader inst = getInstance();
        if (inst.usingStandaloneFile) {
            String value = inst.flatMap.get(dottedKey);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return ConfigurationManager.resolve(dottedKey, defaultValue);
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

    /**
     * Typed view over the Mobile keys consumed by
     * {@code MobileDriverFactory}/crawler classes today (Unified SDK Review
     * Priority 2). Equivalent to calling {@link #get(String, String)}
     * directly, but gives call sites a discoverable, typed API instead of
     * raw dotted-key strings.
     */
    public static MobileConfig getMobileConfig() {
        return new MobileConfig();
    }

    /** Typed, read-only view over Android/iOS/Appium configuration. */
    public static final class MobileConfig {
        private MobileConfig() {
        }

        public String androidAppPath() {
            return get("android.appPath", null);
        }

        public String androidAutomationName() {
            return get("android.automationName", "UiAutomator2");
        }

        public String iosAppPath() {
            return get("ios.appPath", null);
        }

        public String iosAutomationName() {
            return get("ios.automationName", "XCUITest");
        }

        public String appiumLocalUrl() {
            return get("appium.localUrl", "http://127.0.0.1:4723/");
        }
    }
}
