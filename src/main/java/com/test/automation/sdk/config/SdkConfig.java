package com.test.automation.sdk.config;

/**
 * Single, platform-neutral resolver for the SDK configuration directory and
 * its well-known file paths.
 *
 * Phase 3 of the unified web+mobile SDK architecture
 * (docs/proposals/unified-sdk-architect-review.md, section F) -- merges the
 * previously duplicated {@code testbase.SdkConfig} and
 * {@code mobile.testbase.MobileSdkConfig} resolution algorithms into this
 * one class. Both legacy classes now delegate their public fields here, so
 * existing web and mobile consumer code needs no changes.
 *
 * Resolution order (first non-null, non-empty value wins):
 *  1. System property  : -Dsdk.config.dir=&lt;path&gt;
 *  2. Env variable     : SDK_CONFIG_DIR=&lt;path&gt;
 *  3. System property  : -Dmobile.sdk.config.dir=&lt;path&gt;   (legacy mobile override, kept for one release)
 *  4. Env variable     : MOBILE_SDK_CONFIG_DIR=&lt;path&gt;     (legacy mobile override, kept for one release)
 *  5. Default fallback : ./configuration/
 *
 * Usage in a consumer project:
 *   mvn test -Dsdk.config.dir=/path/to/my/configuration
 */
public final class SdkConfig {

    public static final String CONFIG_DIR;
    public static final String CONFIG_PROPERTIES;
    public static final String LOG4J_PROPERTIES;
    public static final String LOG4J2_XML;
    public static final String YAML_CONFIG;
    public static final String MOBILE_CONFIG_YAML;

    /**
     * BrowserStack SDK's javaagent expects this exact filename at the
     * project root -- it is a hard requirement of the BrowserStack Java SDK,
     * not an SDK convention, so it deliberately lives outside CONFIG_DIR.
     */
    public static final String BROWSERSTACK_YAML = "browserstack.yml";

    static {
        String dir = System.getProperty("sdk.config.dir");
        if (dir == null || dir.isEmpty()) {
            dir = System.getenv("SDK_CONFIG_DIR");
        }
        if (dir == null || dir.isEmpty()) {
            dir = System.getProperty("mobile.sdk.config.dir");
        }
        if (dir == null || dir.isEmpty()) {
            dir = System.getenv("MOBILE_SDK_CONFIG_DIR");
        }
        if (dir == null || dir.isEmpty()) {
            dir = "configuration";
        }
        dir = dir.replaceAll("[/\\\\]+$", "");
        CONFIG_DIR         = dir;
        CONFIG_PROPERTIES  = dir + "/config.properties";
        LOG4J_PROPERTIES   = dir + "/log4j.properties";
        LOG4J2_XML         = dir + "/log4j2.xml";
        YAML_CONFIG        = dir + "/sdk-config.yaml";
        MOBILE_CONFIG_YAML = dir + "/mobile-config.yaml";
    }

    private SdkConfig() {}
}
