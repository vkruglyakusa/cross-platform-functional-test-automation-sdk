package com.test.automation.sdk.testbase;

/**
 * Resolves the SDK configuration directory.
 *
 * Resolution order (first non-null, non-empty value wins):
 *  1. System property  : -Dsdk.config.dir=<path>
 *  2. Env variable     : SDK_CONFIG_DIR=<path>
 *  3. Default fallback : ./configuration/
 *
 * Usage in consumer project:
 *   mvn test -Dsdk.config.dir=/path/to/my/configuration
 */
public final class SdkConfig {

    public static final String CONFIG_DIR;
    public static final String CONFIG_PROPERTIES;
    public static final String LOG4J_PROPERTIES;
    public static final String LOG4J2_XML;
    public static final String YAML_CONFIG;

    static {
        String dir = System.getProperty("sdk.config.dir");
        if (dir == null || dir.isEmpty()) {
            dir = System.getenv("SDK_CONFIG_DIR");
        }
        if (dir == null || dir.isEmpty()) {
            dir = "configuration";
        }
        dir = dir.replaceAll("[/\\\\]+$", "");
        CONFIG_DIR        = dir;
        CONFIG_PROPERTIES = dir + "/config.properties";
        LOG4J_PROPERTIES  = dir + "/log4j.properties";
        LOG4J2_XML        = dir + "/log4j2.xml";
        YAML_CONFIG       = dir + "/sdk-config.yaml";
    }

    private SdkConfig() {}
}
