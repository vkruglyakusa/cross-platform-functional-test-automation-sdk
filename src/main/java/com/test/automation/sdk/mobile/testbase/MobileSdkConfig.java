package com.test.automation.sdk.mobile.testbase;

/**
 * Resolves the mobile SDK configuration directory and well-known file names.
 *
 * As of Phase 3 of the unified web+mobile SDK architecture
 * (docs/proposals/unified-sdk-architect-review.md), the resolution algorithm
 * has been merged into {@link com.test.automation.sdk.config.SdkConfig} --
 * this class is kept as a delegator for one release so existing mobile
 * consumer code, and the {@code -Dmobile.sdk.config.dir}/
 * {@code MOBILE_SDK_CONFIG_DIR} overrides, keep working unchanged. New code
 * should reference {@code config.SdkConfig} directly.
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

    public static final String CONFIG_DIR         = com.test.automation.sdk.config.SdkConfig.CONFIG_DIR;
    public static final String MOBILE_CONFIG_YAML = com.test.automation.sdk.config.SdkConfig.MOBILE_CONFIG_YAML;
    public static final String BROWSERSTACK_YAML  = com.test.automation.sdk.config.SdkConfig.BROWSERSTACK_YAML;
    public static final String LOG4J2_XML         = com.test.automation.sdk.config.SdkConfig.LOG4J2_XML;

    private MobileSdkConfig() {}
}
