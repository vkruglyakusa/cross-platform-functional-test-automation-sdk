package com.test.automation.sdk.mobile.testbase;

/**
 * Resolves the mobile SDK configuration directory and well-known file names.
 *
 * Resolution order (first non-null, non-empty value wins), mirroring
 * com.test.automation.sdk.testbase.SdkConfig from the desktop SDK:
 *  1. System property  : -Dmobile.sdk.config.dir=&lt;path&gt;
 *  2. Env variable     : MOBILE_SDK_CONFIG_DIR=&lt;path&gt;
 *  3. Default fallback : ./configuration/
 *
 * Usage in a consumer project:
 *   mvn test -Dmobile.sdk.config.dir=/path/to/my/configuration
 */
public final class MobileSdkConfig {

    public static final String CONFIG_DIR;
    public static final String MOBILE_CONFIG_YAML;
    public static final String BROWSERSTACK_YAML;
    public static final String LOG4J2_XML;

    static {
        String dir = System.getProperty("mobile.sdk.config.dir");
        if (dir == null || dir.isEmpty()) {
            dir = System.getenv("MOBILE_SDK_CONFIG_DIR");
        }
        if (dir == null || dir.isEmpty()) {
            dir = "configuration";
        }
        dir = dir.replaceAll("[/\\\\]+$", "");
        CONFIG_DIR          = dir;
        MOBILE_CONFIG_YAML  = dir + "/mobile-config.yaml";
        BROWSERSTACK_YAML   = "browserstack.yml"; // BrowserStack SDK expects this at the project root
        LOG4J2_XML          = dir + "/log4j2.xml";
    }

    private MobileSdkConfig() {}
}
