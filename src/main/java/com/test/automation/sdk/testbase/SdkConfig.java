package com.test.automation.sdk.testbase;

/**
 * Resolves the SDK configuration directory.
 *
 * As of Phase 3 of the unified web+mobile SDK architecture
 * (docs/proposals/unified-sdk-architect-review.md), the actual resolution
 * algorithm lives in {@link com.test.automation.sdk.config.SdkConfig} --
 * this class now simply delegates its fields there so existing web consumer
 * code needs no changes. That class also recognizes the legacy
 * {@code -Dmobile.sdk.config.dir}/{@code MOBILE_SDK_CONFIG_DIR} overrides as
 * a fallback, so a project configured for mobile-only overrides keeps
 * working if it also uses web-facing SDK classes.
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

    public static final String CONFIG_DIR        = com.test.automation.sdk.config.SdkConfig.CONFIG_DIR;
    public static final String CONFIG_PROPERTIES  = com.test.automation.sdk.config.SdkConfig.CONFIG_PROPERTIES;
    public static final String LOG4J_PROPERTIES   = com.test.automation.sdk.config.SdkConfig.LOG4J_PROPERTIES;
    public static final String LOG4J2_XML         = com.test.automation.sdk.config.SdkConfig.LOG4J2_XML;
    public static final String YAML_CONFIG        = com.test.automation.sdk.config.SdkConfig.YAML_CONFIG;

    private SdkConfig() {}
}
