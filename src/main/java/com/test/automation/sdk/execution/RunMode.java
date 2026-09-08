package com.test.automation.sdk.execution;

/**
 * The execution target a test run is configured for -- local device/browser
 * vs. a remote cloud provider. This is the second of the two axes (the first
 * being {@link Platform}) that the unified execution model switches on.
 *
 * Generalizes {@code com.test.automation.sdk.mobile.execution.ExecutionTarget},
 * which was mobile-only; this version applies equally to web (Selenium local
 * vs. BrowserStack Automate) and mobile (Appium local vs. BrowserStack App
 * Automate).
 */
public enum RunMode {

    /** Local Selenium/Appium server (localhost browser, real device/emulator/simulator). */
    LOCAL,

    /** BrowserStack (Automate for web, App Automate for mobile), via the official BrowserStack Java SDK/javaagent. */
    BROWSERSTACK;

    // Future: GRID, SAUCE_LABS, LAMBDATEST, PERFECTO -- add here + a matching
    // SessionFactory implementation per Platform when a concrete need arises.

    /**
     * Resolves which run mode the current test run is configured for.
     *
     * Resolution order:
     *  1. {@code -Drun.mode=LOCAL|BROWSERSTACK} (canonical, platform-neutral; this is
     *     what new code and future provider profiles should set).
     *  2. Legacy mobile-only {@code -Dmobile.execution.target=LOCAL|BROWSERSTACK}
     *     (kept for backward compatibility with existing mobile Maven profiles).
     *  3. Legacy {@code -DtestInBrowserstack=true|false} flag (still set by the
     *     "browserstack"/"local" Maven profiles) -- {@code true} maps to
     *     {@link #BROWSERSTACK}, {@code false} maps to {@link #LOCAL}.
     *  4. Default: {@link #BROWSERSTACK} (matches the existing default-active Maven profile).
     */
    public static RunMode resolve() {
        String explicit = System.getProperty("run.mode");
        if (explicit != null && !explicit.trim().isEmpty()) {
            return RunMode.valueOf(explicit.trim().toUpperCase());
        }

        String legacyMobileTarget = System.getProperty("mobile.execution.target");
        if (legacyMobileTarget != null && !legacyMobileTarget.trim().isEmpty()) {
            return RunMode.valueOf(legacyMobileTarget.trim().toUpperCase());
        }

        String legacyFlag = System.getProperty("testInBrowserstack");
        if (legacyFlag != null && !legacyFlag.trim().isEmpty()) {
            return Boolean.parseBoolean(legacyFlag) ? BROWSERSTACK : LOCAL;
        }

        return BROWSERSTACK;
    }
}
