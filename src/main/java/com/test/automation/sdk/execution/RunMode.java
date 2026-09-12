package com.test.automation.sdk.execution;

import com.test.automation.sdk.config.ConfigurationManager;

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

    /** @deprecated Use {@link #REMOTE} with provider ID {@code browserstack}. */
    @Deprecated
    BROWSERSTACK,

    /** @deprecated Use {@link #REMOTE} with an appropriate remote Appium provider ID. */
    @Deprecated
    REMOTE_APPIUM,

    /** Generic remote execution mode; requires a provider identifier (e.g., "browserstack", "appium") */
    REMOTE;

    // Future: GRID, SAUCE_LABS, LAMBDATEST, PERFECTO -- add here + a matching
    // SessionFactory implementation per Platform when a concrete need arises.

    /**
     * Resolves which run mode the current test run is configured for.
     *
     * Resolution order:
     *  1. {@code -Drun.mode=LOCAL|BROWSERSTACK|REMOTE_APPIUM|REMOTE} (canonical, platform-neutral; this is
     *     what new code and future provider profiles should set).
     *  2. Legacy mobile-only {@code -Dmobile.execution.target=LOCAL|BROWSERSTACK}
     *     (kept for backward compatibility with existing mobile Maven profiles).
     *  3. Legacy {@code -DtestInBrowserstack=true|false} flag (still set by the
     *     "browserstack"/"local" Maven profiles) -- {@code true} maps to
     *     {@link #BROWSERSTACK}, {@code false} maps to {@link #LOCAL}.
     *  4. Default: {@link #LOCAL}. Remote/cloud execution must be explicitly
     *     opted into via one of the properties above -- upgrading the SDK (or
     *     running with no execution-target property at all) must never
     *     silently start a BrowserStack session.
     */
    public static RunMode resolve() {
        String explicit = System.getProperty("run.mode");
        if (explicit != null && !explicit.trim().isEmpty()) {
            return parse("run.mode", explicit);
        }

        String legacyMobileTarget = System.getProperty("mobile.execution.target");
        if (legacyMobileTarget != null && !legacyMobileTarget.trim().isEmpty()) {
            return parse("mobile.execution.target", legacyMobileTarget);
        }

        String legacyFlag = System.getProperty("testInBrowserstack");
        if (legacyFlag != null && !legacyFlag.trim().isEmpty()) {
            return Boolean.parseBoolean(legacyFlag) ? BROWSERSTACK : LOCAL;
        }

        String configured = ConfigurationManager.resolve("execution.runMode", "");
        if (configured != null && !configured.trim().isEmpty()) {
            return parse("execution.runMode", configured);
        }

        return LOCAL;
    }

    /**
     * Parses a resolved system property value into a {@link RunMode}, failing with a
     * message that names the offending property and its accepted values rather than
     * the raw {@code Enum.valueOf} message.
     */
    private static RunMode parse(String propertyName, String rawValue) {
        String normalized = rawValue.trim().toUpperCase();
        try {
            return RunMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid value '" + rawValue + "' for system property '" + propertyName
                            + "' -- expected one of " + java.util.Arrays.toString(values()),
                    e);
        }
    }
}
