package com.test.automation.sdk.utility;

import java.util.Map;

/**
 * @deprecated Structure Cleanup Phase 2
 * (docs/proposals/sdk-structure-cleanup-assessment-updated-v4.md) moved the
 * real, tested YAML parser and singleton to
 * {@link com.test.automation.sdk.config.YamlConfigReader}. This class is now
 * a thin compatibility facade kept for one release so existing consumer
 * code and any reflection-based lookups of this exact class name keep
 * working unchanged. New code must reference
 * {@link com.test.automation.sdk.config.YamlConfigReader} directly.
 */
@Deprecated
public final class YamlConfigReader {

    public static String get(String key) {
        return com.test.automation.sdk.config.YamlConfigReader.get(key);
    }

    public static String get(String key, String defaultValue) {
        return com.test.automation.sdk.config.YamlConfigReader.get(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return com.test.automation.sdk.config.YamlConfigReader.getBoolean(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return com.test.automation.sdk.config.YamlConfigReader.getInt(key, defaultValue);
    }

    public static Map<String, String> getAll() {
        return com.test.automation.sdk.config.YamlConfigReader.getAll();
    }

    private YamlConfigReader() {}
}