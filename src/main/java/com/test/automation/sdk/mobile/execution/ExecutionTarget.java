package com.test.automation.sdk.mobile.execution;

/**
 * The execution strategy a mobile test run is targeting. This is the single
 * enumeration that the whole execution layer switches on -- adding a new remote
 * provider (Sauce Labs, LambdaTest, Perfecto, ...) means adding one value here plus
 * one new {@link MobileExecutionStrategy} implementation, not touching test code or
 * page objects.
 */
public enum ExecutionTarget {

    /** Local Appium server (localhost, real device/emulator/simulator). */
    LOCAL,

    /** BrowserStack App Automate, via the official BrowserStack Java SDK/javaagent. */
    BROWSERSTACK;

    // Future: SAUCE_LABS, LAMBDATEST, PERFECTO -- add here + a matching
    // MobileExecutionStrategy implementation + Maven profile when a concrete need arises.

    /**
     * Resolves which execution target the current test run is configured for.
     *
     * Resolution order:
     *  1. {@code -Dmobile.execution.target=LOCAL|BROWSERSTACK} (canonical, explicit;
     *     this is what future provider profiles should set).
     *  2. Legacy {@code -DtestInBrowserstack=true|false} flag (still set by the
     *     "browserstack"/"local" Maven profiles for backward compatibility) --
     *     {@code true} maps to {@link #BROWSERSTACK}, {@code false} maps to {@link #LOCAL}.
     *  3. Default: {@link #BROWSERSTACK} (matches the existing default-active Maven profile).
     */
    public static ExecutionTarget resolve() {
        String explicit = System.getProperty("mobile.execution.target");
        if (explicit != null && !explicit.trim().isEmpty()) {
            return ExecutionTarget.valueOf(explicit.trim().toUpperCase());
        }

        String legacyFlag = System.getProperty("testInBrowserstack");
        if (legacyFlag != null && !legacyFlag.trim().isEmpty()) {
            return Boolean.parseBoolean(legacyFlag) ? BROWSERSTACK : LOCAL;
        }

        return BROWSERSTACK;
    }
}
