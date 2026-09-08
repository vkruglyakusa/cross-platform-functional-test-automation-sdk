package com.test.automation.sdk.config;

import java.util.Map;

/**
 * Platform-neutral facade for reading {@code sdk-config.yaml}.
 *
 * Phase 3 of the unified web+mobile SDK architecture
 * (docs/proposals/unified-sdk-architect-review.md) -- the real, already
 * tested parser and singleton still live in
 * {@link com.test.automation.sdk.utility.YamlConfigReader} (left untouched
 * so its existing reflection-based unit tests keep working); this class is
 * simply the new platform-neutral entry point new code should reference.
 *
 * {@code sdk-config.yaml} now also carries built-in defaults for
 * {@code appium.localUrl}, {@code android.automationName}, and
 * {@code ios.automationName} (see {@code YamlConfigReader.loadDefaults()}),
 * so a project's {@code android:}/{@code ios:} sections can live directly in
 * the single unified file. This is what lets
 * {@link com.test.automation.sdk.mobile.config.MobileConfigReader} fall back
 * to reading mobile settings from here once a project no longer ships a
 * standalone {@code mobile-config.yaml}.
 */
public final class YamlConfigReader {

    public static String get(String key) {
        return com.test.automation.sdk.utility.YamlConfigReader.get(key);
    }

    public static String get(String key, String defaultValue) {
        return com.test.automation.sdk.utility.YamlConfigReader.get(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return com.test.automation.sdk.utility.YamlConfigReader.getBoolean(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return com.test.automation.sdk.utility.YamlConfigReader.getInt(key, defaultValue);
    }

    public static Map<String, String> getAll() {
        return com.test.automation.sdk.utility.YamlConfigReader.getAll();
    }

    private YamlConfigReader() {}
}
